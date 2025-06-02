import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

/**
 * Classe que implementa um cliente de chat TCP com interface gráfica Swing
 * Permite conectar a um servidor de chat e trocar mensagens com outros usuários
 */
public class ClienteSwing extends JFrame {
    // Componentes da interface gráfica
    private JTextArea areaTexto;      // Área onde as mensagens são exibidas
    private JTextField campoEntrada;   // Campo onde o usuário digita as mensagens

    // Dados de conexão e comunicação
    private String nomeUsuario;        // Nome do usuário no chat
    private Socket socket;             // Socket para conexão TCP com o servidor
    private ObjectOutputStream outObject;  // Stream para enviar objetos ao servidor
    private ObjectInputStream inObject;    // Stream para receber objetos do servidor

    /**
     * Construtor - configura a interface gráfica do cliente
     */
    public ClienteSwing() {
        // Configurações básicas da janela
        setTitle("Chat TCP - Cliente");
        setSize(500, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);  // Centraliza a janela na tela

        // Criação da área de texto para exibir mensagens (somente leitura)
        areaTexto = new JTextArea();
        areaTexto.setEditable(false);
        add(new JScrollPane(areaTexto), BorderLayout.CENTER);  // Adiciona scroll automático

        // Criação do campo de entrada de texto na parte inferior
        campoEntrada = new JTextField();
        add(campoEntrada, BorderLayout.SOUTH);

        // Listener para quando o usuário pressiona Enter no campo de entrada
        campoEntrada.addActionListener(e -> {
            String conteudo = campoEntrada.getText();
            if (!conteudo.isBlank()) {  // Verifica se não está vazio
                // Formata e exibe a mensagem do próprio usuário na área de texto
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                areaTexto.append("["+ LocalDateTime.now().format(formatter)+"] "+"[Voce] "+conteudo+"\n");

                // Envia a mensagem para o servidor
                enviarMensagem(conteudo);

                // Limpa o campo de entrada
                campoEntrada.setText("");
            }
        });
    }

    /**
     * Metodo responsável por estabelecer conexão com o servidor de chat
     * param host - endereço do servidor
     * param porta - porta do servidor
     * param nomeUsuario - nome desejado pelo usuário
     */
    private void conectar(String host, int porta, String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
        try {
            // Estabelece conexão TCP com o servidor
            InetAddress endereco = InetAddress.getByName(host);
            socket = new Socket(endereco, porta);

            // Cria streams para comunicação com objetos serializados
            outObject = new ObjectOutputStream(socket.getOutputStream());
            inObject = new ObjectInputStream(socket.getInputStream());

            // Loop para validação do nome de usuário
            boolean nomeAceito = false;
            while (!nomeAceito) {
                // Envia tentativa de nome de usuário para o servidor
                Mensagem tentativa = new Mensagem(null, null, nomeUsuario);
                outObject.writeObject(tentativa);
                outObject.flush();

                // Aguarda resposta do servidor sobre a validação do nome
                Mensagem resposta = null;
                try {
                    resposta = (Mensagem) inObject.readObject();
                } catch (ClassNotFoundException e) {
                    areaTexto.append("[Cliente] Erro: classe Mensagem não encontrada.\n");
                    continue;
                }

                // Verifica se o nome foi aceito pelo servidor
                if (resposta.getConteudo().contains("BEM-VINDO AO CHAT")) {
                    this.nomeUsuario = nomeUsuario;
                    areaTexto.append(resposta.getConteudo() + "\n");
                    nomeAceito = true;
                } else {
                    // Nome rejeitado - solicita um novo nome
                    areaTexto.append(resposta.getConteudo() + "\n");
                    nomeUsuario = JOptionPane.showInputDialog(this, "Digite outro nome de usuário:");
                    if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) {
                        socket.close();
                        return;
                    }
                }
            }

            // Cria thread separada para escutar mensagens do servidor
            Thread leituraMensagem = new Thread(() -> {
                try {
                    while (true) {
                        try {
                            // Recebe mensagem do servidor
                            Mensagem recebida = (Mensagem) inObject.readObject();

                            // Verifica se é uma mensagem de desconexão do servidor
                            if (recebida.getRemetente() != null
                                    && recebida.getRemetente().equals("SERVIDOR_DISCONNECT")) {
                                SwingUtilities.invokeLater(() -> {
                                    areaTexto.append("[Cliente] Conexão encerrada.\n");
                                });
                                socket.close();
                                break;
                            }

                            // Formatador para exibir horário das mensagens
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                            // Atualiza a interface gráfica na thread EDT (Event Dispatch Thread)
                            SwingUtilities.invokeLater(() -> {
                                // Verifica se é mensagem privada ou pública
                                if (recebida.getDestinatario() != null && recebida.getDestinatario().equals(this.nomeUsuario)) {
                                    // Mensagem privada
                                    areaTexto.append("[" + recebida.getHorario().format(formatter) + "] [" +
                                            recebida.getRemetente() + " -> Privado] " + recebida.getConteudo() + "\n");
                                } else {
                                    // Mensagem pública
                                    areaTexto.append("[" + recebida.getHorario().format(formatter) + "] [" +
                                            recebida.getRemetente() + "] " + recebida.getConteudo() + "\n");
                                }
                                // Mantém o scroll sempre na última mensagem
                                areaTexto.setCaretPosition(areaTexto.getDocument().getLength());
                            });
                        } catch (java.net.SocketException e) {
                            // Conexão perdida com o servidor
                            SwingUtilities.invokeLater(() -> {
                                areaTexto.append("[Cliente] Conexão perdida com o servidor.\n");
                            });
                            break;
                        } catch (IOException e) {
                            // Erro de comunicação
                            SwingUtilities.invokeLater(() -> {
                                areaTexto.append("[Cliente] Erro de comunicação: " + e.getMessage() + "\n");
                            });
                            break;
                        }
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        areaTexto.append("[Cliente] Listener encerrado.\n");
                    });
                }
            });
            leituraMensagem.start();  // Inicia a thread de escuta

        } catch (IOException e) {
            areaTexto.append("Erro ao conectar: " + e.getMessage() + "\n");
        }
    }

    /**
     * Metodo responsável por processar e enviar mensagens para o servidor
     * Trata comandos especiais como /sair, /privado, /usuarios, /help
     * param conteudo - texto digitado pelo usuário
     */
    private void enviarMensagem(String conteudo) {
        try {
            // Comando para sair do chat
            if (conteudo.trim().equalsIgnoreCase("/sair")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/sair");
                outObject.writeObject(m);
                outObject.flush();
                socket.close();
                System.exit(0);
            }
            // Comando para enviar mensagem privada: /privado:usuario:mensagem
            else if (conteudo.trim().startsWith("/privado:")) {
                String[] palavras = conteudo.split(":");
                if (palavras.length >= 3) {
                    String destinatario = palavras[1];
                    String mensagemPrivada = palavras[0]+":"+palavras[2];
                    Mensagem m = new Mensagem(this.nomeUsuario, destinatario, mensagemPrivada);
                    outObject.writeObject(m);
                    outObject.flush();
                } else {
                    areaTexto.append("Uso correto: /privado:destinatario:mensagem\n");
                }
            }
            // Comando para listar usuários conectados
            else if (conteudo.trim().equalsIgnoreCase("/usuarios")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/usuarios");
                outObject.writeObject(m);
                outObject.flush();
            }
            // Comando para exibir ajuda
            else if (conteudo.trim().equalsIgnoreCase("/help")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/help");
                outObject.writeObject(m);
                outObject.flush();
            }
            // Mensagem normal (pública)
            else {
                Mensagem m = new Mensagem(this.nomeUsuario, null, conteudo);
                outObject.writeObject(m);
                outObject.flush();
            }
        } catch (IOException e) {
            areaTexto.append("Erro ao enviar mensagem: " + e.getMessage() + "\n");
        }
    }

    /**
     * Metodo principal - ponto de entrada da aplicação
     * Solicita nome do usuário e inicia o cliente de chat
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Solicita nome do usuário via dialog
            String nome = JOptionPane.showInputDialog(null, "Digite o nome de cliente:");
            if (nome != null && !nome.trim().isEmpty()) {
                // Cria e exibe a janela do cliente
                ClienteSwing cliente = new ClienteSwing();
                cliente.setVisible(true);
                // Conecta ao servidor (localhost na porta 1234)
                cliente.conectar("localhost", 1234, nome);
            }
        });
    }
}