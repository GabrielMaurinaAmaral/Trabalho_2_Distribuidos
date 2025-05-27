import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Cliente implements Runnable {

    private int porta;
    private String host;
    private String nomeUsuario;

    public Cliente(String host, int porta) {
        this.host = host;
        this.porta = porta;
    }

    public Cliente(String host, int porta, String nomeUsuario) {
        this.host = host;
        this.porta = porta;
        this.nomeUsuario = nomeUsuario;
    }

    @Override
    public void run() {
        try {
            InetAddress endereco = InetAddress.getByName(this.host);
            Socket socket = new Socket(endereco, this.porta);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(this.nomeUsuario);

            ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            Thread leituraMensagem = new Thread(() -> {
                try {
                    while (true) {
                        try {
                            Mensagem recebida = (Mensagem) inObject.readObject();

                            // Verifica se é mensagem de desconexão (conteúdo especial do servidor)
                            if (recebida.getRemetente() != null
                                    && recebida.getRemetente().equals("SERVIDOR_DISCONNECT")) {
                                System.out.println("[Cliente] Conexão encerrada.");
                                socket.close();
                                break;
                            }

                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                            // Verifica se é mensagem privada
                            if (recebida.getDestinatario() != null && recebida.getDestinatario().equals(this.nomeUsuario)) {
                                System.out.println("[" + recebida.getHorario().format(formatter) + "] [" +
                                        recebida.getRemetente() + " -> Privado] " + recebida.getConteudo());
                            } else {
                                // Mensagem pública ou do servidor
                                System.out.println("[" + recebida.getHorario().format(formatter) + "] [" +
                                        recebida.getRemetente() + "] " + recebida.getConteudo());
                            }
                        } catch (java.net.SocketException e) {
                            System.out.println("[Cliente] Conexão perdida com o servidor.");
                            break;
                        } catch (IOException e) {
                            System.out.println("[Cliente] Erro de comunicação: " + e.getMessage());
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Cliente] Listener encerrado.");
                }
            });
            leituraMensagem.start();

            while (true) {
                String conteudo = scanner.nextLine();

                if (conteudo.trim().equalsIgnoreCase("/sair")) {
                    Mensagem m = new Mensagem(this.nomeUsuario, null, "/sair");
                    outObject.writeObject(m);
                    outObject.flush();
                    socket.close();
                    break;
                } else if (conteudo.trim().startsWith("/privado:")) {
                    String[] palavras = conteudo.split(":");
                    if (palavras.length >= 3) {
                        String destinatario = palavras[1];
                        String mensagemPrivada = palavras[0]+":"+palavras[2];
                        Mensagem m = new Mensagem(this.nomeUsuario, destinatario, mensagemPrivada);
                        outObject.writeObject(m);
                        outObject.flush();
                    } else {
                        System.out.println("Uso correto: /privado:destinatario:mensagem");
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
                    // Mensagem para todos
                    Mensagem m = new Mensagem(this.nomeUsuario, null, conteudo);
                    outObject.writeObject(m);
                    outObject.flush();
                }
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Remove apenas uma mensagem de desconexão
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("[Cliente] Digite o nome de cliente: ");
        String nome = scanner.nextLine();

        Thread cliente = new Thread(new Cliente("localhost", 1234, nome));
        cliente.start();
    }
}