import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * Classe que implementa um cliente de chat TCP via linha de comando
 * Permite conectar ao servidor e trocar mensagens com outros usuários
 */
public class Cliente implements Runnable {

    private int porta; // Porta do servidor
    private String host; // Endereço do servidor
    private String nomeUsuario; // Nome do usuário no chat

    /**
     * Construtor para conexão sem nome pré-definido
     * param host - endereço do servidor
     * param porta - porta do servidor
     */
    public Cliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
    }

    /**
     * Construtor para conexão com nome pré-definido
     * param host - endereço do servidor
     * param porta - porta do servidor
     * param nomeUsuario - nome desejado para o chat
     */
    public Cliente(String host, int porta, String nomeUsuario) {
        this.host = host;
        this.porta = porta;
        this.nomeUsuario = nomeUsuario;
    }

    /**
     * Metodo principal que implementa a lógica do cliente
     * Conecta ao servidor e gerencia comunicação bidirecional
     */
    @Override
    public void run() {
        try {
            // Estabelece conexão TCP com o servidor
            InetAddress endereco = InetAddress.getByName(this.host);
            Socket socket = new Socket(endereco, this.porta);

            // Cria streams para comunicação com o servidor
            ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            // Processo de autenticação - validação do nome de usuário
            boolean nomeAceito = false;
            while (!nomeAceito) {
                System.out.print("[Cliente] Digite seu nome de usuario: ");
                String tentativaNome = scanner.nextLine();

                // Envia tentativa de nome para o servidor
                Mensagem tentativa = new Mensagem(null, null, tentativaNome);
                outObject.writeObject(tentativa);
                outObject.flush();

                // Recebe resposta do servidor
                Mensagem resposta = null;
                try {
                    resposta = (Mensagem) inObject.readObject();
                } catch (ClassNotFoundException e) {
                    System.out.println("[Cliente] Erro: classe Mensagem nao encontrada.");
                    continue;
                }

                // Verifica se nome foi aceito (mensagem de boas-vindas)
                if (resposta.getConteudo().contains("BEM-VINDO AO CHAT")) {
                    this.nomeUsuario = tentativaNome;
                    System.out.println(resposta.getConteudo());
                    nomeAceito = true;
                } else {
                    // Nome rejeitado - mostra erro e solicita novo nome
                    System.out.println(resposta.getConteudo());
                }
            }

            // Cria thread separada para receber mensagens do servidor
            // Isso permite receber mensagens enquanto o usuário digita
            Thread leituraMensagem = new Thread(() -> {
                try {
                    while (true) {
                        try {
                            // Lê mensagem recebida do servidor
                            Mensagem recebida = (Mensagem) inObject.readObject();

                            // Verifica se é mensagem especial de desconexão do servidor
                            if (recebida.getRemetente() != null
                                    && recebida.getRemetente().equals("SERVIDOR_DISCONNECT")) {
                                System.out.println("[Cliente] Conexao encerrada.");
                                socket.close();
                                break;
                            }

                            // Formata timestamp para exibição
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                            // Diferencia mensagens privadas das públicas na exibição
                            if (recebida.getDestinatario() != null && recebida.getDestinatario().equals(this.nomeUsuario)) {
                                // Mensagem privada recebida
                                System.out.println("[" + recebida.getHorario().format(formatter) + "] [" +
                                        recebida.getRemetente() + " -> Privado] " + recebida.getConteudo());
                            } else {
                                // Mensagem pública ou do servidor
                                System.out.println("[" + recebida.getHorario().format(formatter) + "] [" +
                                        recebida.getRemetente() + "] " + recebida.getConteudo());
                            }
                        } catch (java.net.SocketException e) {
                            System.out.println("[Cliente] Conexao perdida com o servidor.");
                            break;
                        } catch (IOException e) {
                            System.out.println("[Cliente] Erro de comunicacao: " + e.getMessage());
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Cliente] Listener encerrado.");
                }
            });
            leituraMensagem.start(); // Inicia thread de recepção

            // Loop principal para envio de mensagens
            while (true) {
                String conteudo = scanner.nextLine();

                // Comando para sair do chat
                if (conteudo.trim().equalsIgnoreCase("/sair")) {
                    Mensagem m = new Mensagem(this.nomeUsuario, null, "/sair");
                    outObject.writeObject(m);
                    outObject.flush();
                    socket.close();
                    break;
                }

                // Comando para mensagem privada: /privado:destinatario:mensagem
                else if (conteudo.trim().startsWith("/privado:")) {
                    String[] palavras = conteudo.split(":");
                    if (palavras.length >= 3) {
                        String destinatario = palavras[1];
                        // Reconstrói mensagem mantendo o formato para o servidor
                        String mensagemPrivada = palavras[0]+":"+palavras[2];
                        Mensagem m = new Mensagem(this.nomeUsuario, destinatario, mensagemPrivada);
                        outObject.writeObject(m);
                        outObject.flush();
                    } else {
                        System.out.println("Uso correto: /privado:destinatario:mensagem");
                    }
                }

                // Comando para listar usuários online
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

                // Mensagem broadcast (para todos os usuários)
                else {
                    Mensagem m = new Mensagem(this.nomeUsuario, null, conteudo);
                    outObject.writeObject(m);
                    outObject.flush();
                }
            }
            socket.close(); // Fecha conexão ao sair
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Bloco de limpeza - executado sempre ao finalizar
        }
    }

    /**
     * Metodo main - ponto de entrada do programa cliente
     * Cria e inicia cliente conectando ao localhost na porta 1234
     */
    public static void main(String[] args) {
        Thread cliente = new Thread(new Cliente("localhost", 1234));
        cliente.start();
    }
}