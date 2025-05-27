import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public class ClienteSwing extends JFrame {
    private JTextArea areaTexto;
    private JTextField campoEntrada;
    private String nomeUsuario;
    private Socket socket;
    private ObjectOutputStream outObject;
    private ObjectInputStream inObject;

    public ClienteSwing() {
        setTitle("Chat TCP - Cliente");
        setSize(500, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        areaTexto = new JTextArea();
        areaTexto.setEditable(false);
        add(new JScrollPane(areaTexto), BorderLayout.CENTER);

        campoEntrada = new JTextField();
        add(campoEntrada, BorderLayout.SOUTH);

        campoEntrada.addActionListener(e -> {
            String conteudo = campoEntrada.getText();
            if (!conteudo.isBlank()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                areaTexto.append("["+ LocalDateTime.now().format(formatter)+"] "+"[Voce] "+conteudo+"\n");
                enviarMensagem(conteudo);
                campoEntrada.setText("");
            }
        });
    }

    private void conectar(String host, int porta, String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
        try {
            InetAddress endereco = InetAddress.getByName(host);
            socket = new Socket(endereco, porta);

            outObject = new ObjectOutputStream(socket.getOutputStream());
            inObject = new ObjectInputStream(socket.getInputStream());

        boolean nomeAceito = false;
        while (!nomeAceito) {
            Mensagem tentativa = new Mensagem(null, null, nomeUsuario);
            outObject.writeObject(tentativa);
            outObject.flush();

            Mensagem resposta = null;
            try {
                resposta = (Mensagem) inObject.readObject();
            } catch (ClassNotFoundException e) {
                areaTexto.append("[Cliente] Erro: classe Mensagem não encontrada.\n");
                continue;
            }
            if (resposta.getConteudo().contains("BEM-VINDO AO CHAT")) {
                this.nomeUsuario = nomeUsuario;
                areaTexto.append(resposta.getConteudo() + "\n");
                nomeAceito = true;
            } else {
                areaTexto.append(resposta.getConteudo() + "\n");
                nomeUsuario = JOptionPane.showInputDialog(this, "Digite outro nome de usuário:");
                if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) {
                    socket.close();
                    return;
                }
            }
        }

            Thread leituraMensagem = new Thread(() -> {
                try {
                    while (true) {
                        try {
                            Mensagem recebida = (Mensagem) inObject.readObject();

                            if (recebida.getRemetente() != null
                                    && recebida.getRemetente().equals("SERVIDOR_DISCONNECT")) {
                                SwingUtilities.invokeLater(() -> {
                                    areaTexto.append("[Cliente] Conexão encerrada.\n");
                                });
                                socket.close();
                                break;
                            }

                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                            SwingUtilities.invokeLater(() -> {
                                if (recebida.getDestinatario() != null && recebida.getDestinatario().equals(this.nomeUsuario)) {
                                    areaTexto.append("[" + recebida.getHorario().format(formatter) + "] [" +
                                            recebida.getRemetente() + " -> Privado] " + recebida.getConteudo() + "\n");
                                } else {
                                    areaTexto.append("[" + recebida.getHorario().format(formatter) + "] [" +
                                            recebida.getRemetente() + "] " + recebida.getConteudo() + "\n");
                                }
                                areaTexto.setCaretPosition(areaTexto.getDocument().getLength());
                            });
                        } catch (java.net.SocketException e) {
                            SwingUtilities.invokeLater(() -> {
                                areaTexto.append("[Cliente] Conexão perdida com o servidor.\n");
                            });
                            break;
                        } catch (IOException e) {
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
            leituraMensagem.start();

        } catch (IOException e) {
            areaTexto.append("Erro ao conectar: " + e.getMessage() + "\n");
        }
    }

    private void enviarMensagem(String conteudo) {
        try {
            if (conteudo.trim().equalsIgnoreCase("/sair")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/sair");
                outObject.writeObject(m);
                outObject.flush();
                socket.close();
                System.exit(0);
            } else if (conteudo.trim().startsWith("/privado:")) {
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
            } else if (conteudo.trim().equalsIgnoreCase("/usuarios")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/usuarios");
                outObject.writeObject(m);
                outObject.flush();
            } else if (conteudo.trim().equalsIgnoreCase("/help")) {
                Mensagem m = new Mensagem(this.nomeUsuario, null, "/help");
                outObject.writeObject(m);
                outObject.flush();
            } else {
                Mensagem m = new Mensagem(this.nomeUsuario, null, conteudo);
                outObject.writeObject(m);
                outObject.flush();
            }
        } catch (IOException e) {
            areaTexto.append("Erro ao enviar mensagem: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String nome = JOptionPane.showInputDialog(null, "Digite o nome de cliente:");
            if (nome != null && !nome.trim().isEmpty()) {
                ClienteSwing cliente = new ClienteSwing();
                cliente.setVisible(true);
                cliente.conectar("localhost", 1234, nome);
            }
        });
    }
}