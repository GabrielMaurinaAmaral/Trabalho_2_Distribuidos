import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe responsável por implementar um servidor de chat TCP
 * que permite múltiplos clientes conectados simultaneamente
 */
public class Servidor {

    private int porta; // Porta onde o servidor irá escutar conexões

    /**
     * Construtor que inicializa o servidor com uma porta específica
     * param porta - porta de escuta do servidor
     */
    public Servidor(int porta) {
        this.porta = porta;
    }

    /**
     * Classe interna que armazena informações de cada cliente conectado
     * Mantém referência do socket e stream de saída para envio de mensagens
     */
    static class ClienteInfo {
        private Socket socket; // Conexão TCP do cliente
        private ObjectOutputStream out; // Stream para enviar objetos ao cliente

        ClienteInfo(Socket socket, ObjectOutputStream out) {
            this.socket = socket;
            this.out = out;
        }
    }

    // Map thread-safe que armazena todos os clientes conectados
    // Chave: nome do usuário, Valor: informações do cliente
    private static ConcurrentHashMap<String, ClienteInfo> clientesConectados = new ConcurrentHashMap<>();

    /**
     * Metodo principal que inicia o servidor e aceita conexões dos clientes
     * Implementa o loop principal de aceitação de conexões
     */
    public void run() throws IOException {
        // Cria o socket servidor na porta especificada
        ServerSocket conexao = new ServerSocket(this.porta);

        try {
            // Loop infinito para aceitar conexões
            while (true) {
                System.out.println("[Servidor] Aguardando conexoes na porta " + conexao.getLocalPort() + "...");

                // Bloqueia até que um cliente se conecte
                Socket socket = conexao.accept();

                // Cria streams para comunicação com o cliente
                ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

                // Processo de validação do nome de usuário
                String nomeUsuario = null;
                while (true) {
                    Mensagem resposta = null;
                    try {
                        // Lê a tentativa de nome enviada pelo cliente
                        resposta = (Mensagem) inObject.readObject();
                    } catch (ClassNotFoundException e) {
                        // Envia erro caso não consiga deserializar a mensagem
                        outObject.writeObject(new Mensagem("Servidor", null, "Erro interno de leitura de objeto."));
                        outObject.flush();
                        continue;
                    }
                    String nomeTentativa = resposta.getConteudo();

                    // Validação: nome deve conter apenas letras e números
                    if (!nomeTentativa.matches("[a-zA-Z0-9]+")) {
                        outObject.writeObject(
                                new Mensagem("Servidor", null, "Nome invalido! Use apenas letras e numeros."));
                        outObject.flush();
                    }
                    // Validação: nome não pode estar em uso
                    else if (clientesConectados.containsKey(nomeTentativa)) {
                        outObject.writeObject(new Mensagem("Servidor", null, "Nome ja esta em uso! Escolha outro."));
                        outObject.flush();
                    }
                    // Nome válido e disponível
                    else {
                        nomeUsuario = nomeTentativa;
                        break; // Sai do loop de validação
                    }
                }

                // Adiciona o cliente à lista de conectados
                clientesConectados.put(nomeUsuario, new ClienteInfo(socket, outObject));

                System.out.println("[Servidor] Conexao aceita: " + nomeUsuario + ":" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());

                // Envia mensagem de boas-vindas com lista de comandos disponíveis
                String mensagemBoasVindas = "\n=== BEM-VINDO AO CHAT ===\n" +
                        "Comandos disponiveis:\n" +
                        "- /usuarios - Exibe lista de usuarios online\n" +
                        "- /privado:destinatario:mensagem - Envia mensagem privada\n" +
                        "- /help - Mostra esta lista de comandos\n" +
                        "- /sair - Desconecta do chat\n" +
                        "- Para enviar mensagem para todos, apenas digite a mensagem\n" +
                        "========================";

                Mensagem bemVindo = new Mensagem("Servidor", nomeUsuario, mensagemBoasVindas);
                outObject.writeObject(bemVindo);
                outObject.flush();

                // Notifica todos os outros clientes sobre a entrada do novo usuário
                String entrou = nomeUsuario+" entrou no servidor";
                for (String id : clientesConectados.keySet()) {
                    if(!id.equals(nomeUsuario)) { // Não envia para o próprio usuário
                        try {
                            ClienteInfo clienteInfo = clientesConectados.get(id);
                            // Verifica se a conexão ainda está ativa
                            if (!clienteInfo.socket.isClosed()) {
                                clienteInfo.out.writeObject(new Mensagem("Servidor", null, entrou));
                                clienteInfo.out.flush();
                            } else {
                                // Remove cliente desconectado
                                clientesConectados.remove(id);
                            }
                        } catch (IOException e) {
                            // Remove cliente com erro de conexão
                            clientesConectados.remove(id);
                            System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao.");
                        }
                    }
                }

                // Cria thread dedicada para processar mensagens deste cliente
                Thread cliente = new Thread(new Processador(nomeUsuario, socket, outObject, inObject));
                cliente.start();
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[Servidor] Conexao encerrada por inatividade.");
            conexao.close();
        }
    }

    /**
     * Classe interna que implementa Runnable para processar mensagens de cada cliente
     * Cada cliente conectado terá sua própria instância desta classe executando em thread separada
     */
    class Processador implements Runnable {
        private String idCliente; // Nome/ID do cliente
        private Socket socket; // Socket de conexão
        private ObjectOutputStream outObject; // Stream de saída
        private ObjectInputStream inObject; // Stream de entrada

        /**
         * Construtor que inicializa o processador para um cliente específico
         */
        public Processador(String idCliente, Socket socket, ObjectOutputStream outObject, ObjectInputStream inObject) {
            this.idCliente = idCliente;
            this.socket = socket;
            this.outObject = outObject;
            this.inObject = inObject;
        }

        /**
         * Metodo principal da thread que processa mensagens do cliente
         * Implementa a lógica de chat com comandos e broadcast
         */
        @Override
        public void run() {
            boolean conectado = true;
            boolean saidaVoluntaria = false; // Flag para distinguir tipos de desconexão

            try  {
                // Loop principal de processamento de mensagens
                while (conectado) {
                    // Lê mensagem enviada pelo cliente
                    Mensagem m = (Mensagem) inObject.readObject();
                    System.out.println("[Servidor] Recebi mensagem " + m.toString() + " de "
                            + this.socket.getInetAddress().getHostAddress() + ":" + this.socket.getPort());

                    // Processamento do comando /sair
                    if (m.getConteudo() != null && m.getConteudo().trim().equals("/sair")) {
                        System.out.println("[Servidor] Cliente " + this.idCliente + " solicitou desconexao.");
                        conectado = false;
                        saidaVoluntaria = true;
                        notificarSaidaUsuario(); // Notifica outros usuários
                        break;
                    }

                    // Processamento de mensagens privadas
                    // Mensagem privada é identificada quando há destinatário definido
                    else if (m.getDestinatario() != null ) {
                        boolean usuarioEncontrado = false;

                        // Procura o destinatário na lista de clientes conectados
                        for (String id : clientesConectados.keySet()) {
                            if (id.equals(m.getDestinatario())) {
                                ClienteInfo clienteInfo = clientesConectados.get(id);
                                try {
                                    // Verifica se o destinatário ainda está conectado
                                    if (!clienteInfo.socket.isClosed()) {
                                        // Extrai a mensagem do formato "/privado:destinatario:mensagem"
                                        String[] palavras = m.getConteudo().split(":");
                                        clienteInfo.out.writeObject(new Mensagem(m.getRemetente(), m.getDestinatario(), palavras[1]));
                                        clienteInfo.out.flush();
                                        usuarioEncontrado = true;
                                    }
                                    else {
                                        // Remove cliente desconectado
                                        clientesConectados.remove(id);
                                    }
                                }
                                catch (IOException e) {
                                    // Remove cliente com erro de conexão
                                    clientesConectados.remove(id);
                                    System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao");
                                }
                                break;
                            }
                        }

                        // Informa erro se usuário não foi encontrado
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

                    // Processamento de comandos (começam com "/")
                    else if (m.getConteudo() != null && m.getConteudo().trim().startsWith("/")) {
                        String conteudo = m.getConteudo().trim();

                        // Comando /usuarios - lista usuários online
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
                        }

                        // Comando /help - mostra ajuda
                        else if (conteudo.equals("/help")) {
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
                        }

                        // Comando desconhecido
                        else {
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

                    // Mensagem broadcast (para todos os usuários)
                    else {
                        // Envia mensagem para todos os clientes conectados, exceto o remetente
                        for (String id : clientesConectados.keySet()) {
                            if (!id.equals(this.idCliente)) { // Não envia para si mesmo
                                try {
                                    ClienteInfo clienteInfo = clientesConectados.get(id);
                                    if (!clienteInfo.socket.isClosed()) {
                                        clienteInfo.out.writeObject(new Mensagem(m.getRemetente(), null, m.getConteudo()));
                                        clienteInfo.out.flush();
                                    } else {
                                        // Remove cliente desconectado
                                        clientesConectados.remove(id);
                                    }
                                } catch (IOException e) {
                                    // Remove cliente com erro de conexão
                                    clientesConectados.remove(id);
                                    System.out.println("[Servidor] Cliente " + id + " removido por erro de conexao.");
                                }
                            }
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                // Cliente desconectou inesperadamente
                System.out.println("[Servidor] Cliente " + this.idCliente + " desconectado inesperadamente.");
                conectado = false;
                saidaVoluntaria = false;
            } finally {
                // Limpeza: remove cliente da lista e fecha conexão
                clientesConectados.remove(this.idCliente);

                // Notifica saída apenas se não foi saída voluntária (evita duplicação)
                if (!saidaVoluntaria) {
                    notificarSaidaUsuario();
                }

                try {
                    if (!this.socket.isClosed()) {
                        // Envia sinal de desconexão para cliente se não foi saída voluntária
                        if (!saidaVoluntaria) {
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

        /**
         * Metodo auxiliar para notificar todos os clientes sobre a saída de um usuário
         * Evita duplicação de código entre saída voluntária e desconexão inesperada
         */
        private void notificarSaidaUsuario() {
            Mensagem notificacaoSaida = new Mensagem("Servidor", null,
                    this.idCliente + " saiu do chat.");

            // Envia notificação para todos os clientes conectados
            for (String id : clientesConectados.keySet()) {
                if (!id.equals(this.idCliente)) {
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
        }
    }

    /**
     * Metodo main - ponto de entrada do programa
     * Cria e inicia o servidor na porta 1234
     */
    public static void main(String[] args) {
        Servidor servidor = new Servidor(1234);

        try {
            servidor.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}