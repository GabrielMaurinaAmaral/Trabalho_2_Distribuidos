import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {

	private int porta;
	private ServerSocket conexao;
	private Socket socket;
	private static ConcurrentHashMap<String, Socket> clientesConectados = new ConcurrentHashMap<>();

	public Servidor(int porta) {
		this.porta = porta;
	}

	public void run() throws IOException {
		conexao = new ServerSocket(porta);

		try {
			while (true) {

				System.out.println("[Servidor] Aguardando conexões na porta " + conexao.getLocalPort() + "...");

				conexao.setSoTimeout(0);
				socket = conexao.accept();
				String idCliente = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
				clientesConectados.put(idCliente, socket);

				System.out.println("[Servidor] Conexão aceita: " + idCliente);
				System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());

				Thread cliente = new Thread(new Processador(socket, idCliente));
				cliente.start();
			}
		} catch (SocketTimeoutException e) {
			System.out.println("[Servidor] Conexão encerrada por inatividade.");
			conexao.close();
		}
	}

	class Processador implements Runnable {
		private Socket socket;
		private String idCliente;
		DataInputStream in;
		DataOutputStream out;

		public Processador(Socket socket, String idCliente) {
			this.socket = socket;
			this.idCliente = idCliente;
		}

		@Override
		public void run() {
			try {
				socket.setSoTimeout(60000);

				ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());
				ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());

				int aux = 1;

				while (aux == 1) {
					Mensagem m = (Mensagem) inObject.readObject();
					System.out.println("[Servidor] Recebi a pessoa " + m.toString() + " de "
							+ socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
				}

				socket.close();

			} catch (SocketTimeoutException e) {
				System.out.println("[Servidor] Conexão com o cliente " + socket.getInetAddress().getHostAddress() + ":"
						+ socket.getPort() + " encerrada por inatividade.");
				try {
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				clientesConectados.remove(idCliente);
				System.out.println("[Servidor] Cliente removido: " + idCliente);
				System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());
			}
		}
	}

	public static void main(String[] args) {

		Servidor servidor = new Servidor(1234);

		try {
			servidor.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}