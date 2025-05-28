import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {

    private int porta;

    public Servidor(int porta) {
        this.porta = porta;
    }

    static class ClienteInfo {
        private Socket socket;
        private ObjectOutputStream out;

        ClienteInfo(Socket socket, ObjectOutputStream out) {
            this.socket = socket;
            this.out = out;
        }
    }

    private static ConcurrentHashMap<String, ClienteInfo> clientesConectados = new ConcurrentHashMap<>();

    public void run() throws IOException {
        ServerSocket conexao = new ServerSocket(this.porta);

        try {
            while (true) {
                System.out.println("[Servidor] Aguardando conexoes na porta " + conexao.getLocalPort() + "...");
                Socket socket = conexao.accept();

                ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

                String nomeUsuario = null;
                while (true) {
                    Mensagem resposta = null;
                    try {
                        resposta = (Mensagem) inObject.readObject();
                    } catch (ClassNotFoundException e) {
                        outObject.writeObject(new Mensagem("Servidor", null, "Erro interno de leitura de objeto."));
                        outObject.flush();
                        continue;
                    }
                    String nomeTentativa = resposta.getConteudo();

                    if (!nomeTentativa.matches("[a-zA-Z0-9]+")) {
                        outObject.writeObject(
                                new Mensagem("Servidor", null, "Nome inválido! Use apenas letras e números."));
                        outObject.flush();
                    } else if (clientesConectados.containsKey(nomeTentativa)) {
                        outObject.writeObject(new Mensagem("Servidor", null, "Nome já está em uso! Escolha outro."));
                        outObject.flush();
                    } else {
                        nomeUsuario = nomeTentativa;
                        break;
                    }
                }

                clientesConectados.put(nomeUsuario, new ClienteInfo(socket, outObject));

                System.out.println("[Servidor] Conexao aceita: " + nomeUsuario + ":" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());

                // Envia mensagem de boas-vindas
                String mensagemBoasVindas = "\n=== BEM-VINDO AO CHAT ===\n" +
                        "Comandos disponíveis:\n" +
                        "- /usuarios - Exibe lista de usuarios online\n" +
                        "- /privado:destinatario:mensagem - Envia mensagem privada\n" +
                        "- /help - Mostra esta lista de comandos\n" +
                        "- /sair - Desconecta do chat\n" +
                        "- Para enviar mensagem para todos, apenas digite a mensagem\n" +
                        "========================";

                Mensagem bemVindo = new Mensagem("Servidor", nomeUsuario, mensagemBoasVindas);
                outObject.writeObject(bemVindo);
                outObject.flush();
				String entrou = nomeUsuario+" entrou no servidor";
                    for (String id : clientesConectados.keySet()) {
                        if(!id.equals(nomeUsuario)) {
                            try {
                                ClienteInfo clienteInfo = clientesConectados.get(id);
                                if (!clienteInfo.socket.isClosed()) {
                                    clienteInfo.out.writeObject(new Mensagem("Servidor", null, entrou));
                                    clienteInfo.out.flush();
                                } else {
                                    clientesConectados.remove(id);
                                }
                            } catch (IOException e) {
                                clientesConectados.remove(id);
                                System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao.");
                            }

                        }
                }
                Thread cliente = new Thread(new Processador(nomeUsuario, socket, outObject, inObject));
                cliente.start();
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[Servidor] Conexao encerrada por inatividade.");
            conexao.close();
        }
    }

    class Processador implements Runnable {
        private String idCliente;
        private Socket socket;
        private ObjectOutputStream outObject;
        private ObjectInputStream inObject;

        public Processador(String idCliente, Socket socket, ObjectOutputStream outObject, ObjectInputStream inObject) {
            this.idCliente = idCliente;
            this.socket = socket;
            this.outObject = outObject;
            this.inObject = inObject;
        }

        @Override
        public void run() {
            boolean conectado = true;
            try  {
                this.socket.setSoTimeout(60000);

                while (conectado) {
                    Mensagem m = (Mensagem) inObject.readObject();
                    System.out.println("[Servidor] Recebi mensagem " + m.toString() + " de "
                            + this.socket.getInetAddress().getHostAddress() + ":" + this.socket.getPort());

                    if (m.getConteudo() != null && m.getConteudo().trim().equals("/sair")) {
                        System.out.println("[Servidor] Cliente " + this.idCliente + " solicitou desconexão.");
                        conectado = false;

                        // Notifica outros usuários sobre a saída
                        Mensagem notificacaoSaida = new Mensagem("Servidor", null,
                                this.idCliente + " saiu do chat.");

                        for (String id : clientesConectados.keySet()) {
                            if (!id.equals(this.idCliente)) {
                                try {
                                    ClienteInfo clienteInfo = clientesConectados.get(id);
                                    if (!clienteInfo.socket.isClosed()) {
                                        clienteInfo.out.writeObject(notificacaoSaida);
                                        clienteInfo.out.flush();
                                    }
                                } catch (IOException e) {
                                    // Ignora erros ao notificar
                                }
                            }
                        }
                        break;
                    }

                    // Verifica se é mensagem privada
                    else if (m.getDestinatario() != null ) {
                        boolean usuarioEncontrado = false;
                        for (String id : clientesConectados.keySet()) {
                            // Verifica se o destinatário é o mesmo que o id do cliente
                            if (id.equals(m.getDestinatario())) {
                                // pega as informações do cliente que quer conversar no privado
                                ClienteInfo clienteInfo = clientesConectados.get(id);
                                // tenta enviar a mensagem privada
                                try {
                                    // Verifica se o socket do cliente destinatario está aberto
                                    if (!clienteInfo.socket.isClosed()) {
                                        String[] palavras = m.getConteudo().split(":");
                                        clienteInfo.out.writeObject(new Mensagem(m.getRemetente(), m.getDestinatario(), palavras[1]));
                                        clienteInfo.out.flush();
                                        usuarioEncontrado = true;
                                    }
                                    // caso esteja fechado, remove o cliente da lista de conectados
                                    else {
                                        clientesConectados.remove(id);
                                    }
                                }
                                // caso ocorra algum erro ao enviar a mensagem, remove o cliente da lista de conectados
                                catch (IOException e) {
                                    clientesConectados.remove(id);
                                    System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao");
                                }
                                break;
                            }
                        }
                        // Se o usuário não foi encontrado, envia mensagem de erro para o remetente
                        if (!usuarioEncontrado) {
                            try {
                                this.outObject.writeObject(new Mensagem("Servidor", this.idCliente,
                                        "Usuario '" + m.getDestinatario() + "' nao encontrado!"));
                                this.outObject.flush();
                            } catch (IOException e) {
                                System.out.println("[Servidor] Erro ao enviar mensagem de erro para " + this.idCliente);
                                conectado = false;
                            }
                        }
                    }
                    // Verifica se é comando ou broadcast
                    else if (m.getConteudo() != null && m.getConteudo().trim().startsWith("/")) {
                        String conteudo = m.getConteudo().trim();

                        if (conteudo.equals("/usuarios")) {
                            StringBuilder lista = new StringBuilder("\n=================\nUsuarios online: \n");
                            for (String id : clientesConectados.keySet()) {
                                lista.append("-");
                                lista.append(id).append("\n");
                            }
                            lista.append("=================");
                            try {
                                this.outObject.writeObject(new Mensagem("Servidor", this.idCliente, lista.toString()));
                                this.outObject.flush();
                            } catch (IOException e) {
                                System.out.println("[Servidor] Erro ao enviar lista de usuarios para " + this.idCliente);
                                conectado = false;
                            }
                        } else if (conteudo.equals("/help")) {
                            String ajuda = "\n=== COMANDOS DISPONIVEIS ===\n" +
                                    "- /usuarios - Exibe lista de usuarios online\n" +
                                    "- /privado:destinatario:mensagem - Envia mensagem privada\n" +
                                    "- /help - Mostra esta lista de comandos\n" +
                                    "- /sair - Desconecta do chat\n" +
                                    "- Para enviar mensagem para todos, apenas digite a mensagem\n" +
                                    "============================";
                            try {
                                this.outObject.writeObject(new Mensagem("Servidor", this.idCliente, ajuda));
                                this.outObject.flush();
                            } catch (IOException e) {
                                System.out.println("[Servidor] Erro ao enviar ajuda para " + this.idCliente);
                                conectado = false;
                            }
                        } else {
                            // Comando desconhecido
                            String mensagemErro = "Comando '" + conteudo.split(":")[0] + "' nao existe!\n" +
                                    "Para ver os comandos disponiveis, digite /help";

                            try {
                                this.outObject.writeObject(new Mensagem("Servidor", this.idCliente, mensagemErro));
                                this.outObject.flush();
                            } catch (IOException e) {
                                System.out.println("[Servidor] Erro ao enviar mensagem de erro para " + this.idCliente);
                                conectado = false;
                            }
                        }
                    }
                    // Mensagem broadcast normal
                    else {
                        for (String id : clientesConectados.keySet()) {
                            if (!id.equals(this.idCliente)) {
                                try {
                                    ClienteInfo clienteInfo = clientesConectados.get(id);
                                    if (!clienteInfo.socket.isClosed()) {
                                        clienteInfo.out.writeObject(new Mensagem(m.getRemetente(), null, m.getConteudo()));
                                        clienteInfo.out.flush();
                                    } else {
                                        clientesConectados.remove(id);
                                    }
                                } catch (IOException e) {
                                    clientesConectados.remove(id);
                                    System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao.");
                                }
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[Servidor] Conexao com o cliente " + this.idCliente + " encerrada por inatividade.");
                conectado = false;
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("[Servidor] Cliente " + this.idCliente + " desconectado inesperadamente.");
                conectado = false;
            } finally {
                clientesConectados.remove(this.idCliente);

                // Notifica outros usuários sobre a desconexão inesperada (se não foi /sair)
                if (conectado) {
                    Mensagem notificacaoSaida = new Mensagem("Servidor", null,
                            this.idCliente + " saiu do chat.");

                    for (String id : clientesConectados.keySet()) {
                        try {
                            ClienteInfo clienteInfo = clientesConectados.get(id);
                            if (!clienteInfo.socket.isClosed()) {
                                clienteInfo.out.writeObject(notificacaoSaida);
                                clienteInfo.out.flush();
                            }
                        } catch (IOException e) {
                            // Ignora erros ao notificar outros clientes
                        }
                    }
                }

                try {
                    if (!this.socket.isClosed()) {
                        if (conectado) {
                            this.outObject.writeObject(new Mensagem("SERVIDOR_DISCONNECT", null, null));
                            this.outObject.flush();
                        }
                        this.socket.close();
                    }
                } catch (IOException e) {
                    System.out.println("[Servidor] Socket do cliente " + this.idCliente + " ja estava fechado.");
                }

                System.out.println("[Servidor] Cliente removido: " + this.idCliente);
                System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());
            }
        }
    }

    public static void main(String[] args) {
        Servidor servidor = new Servidor(1234);

        try {
            servidor.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}