import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Servidor {

	private int porta;
	private ServerSocket conexao;
	Socket socket;

	public Servidor(int porta) {
		this.porta = porta;
	}

	public void run() throws IOException {
		conexao = new ServerSocket(porta);

		try {
			while (true) {
				System.out.println("[Servidor] Aguardando conexões na porta " + conexao.getLocalPort() + "...");

				conexao.setSoTimeout(20000);
				socket = conexao.accept();
				System.out.println("[Servidor] Conexão aceita ..." + socket.getLocalPort());

				Thread cliente = new Thread(new Processador(socket));
				cliente.start();
			}
		} catch (SocketTimeoutException e) {
			System.out.println("[Servidor] Conexão encerrada por inatividade.");
			conexao.close();
		}
	}

	class Processador implements Runnable {
		private Socket socket;
		DataInputStream in;
		DataOutputStream out;

		public Processador(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				socket.setSoTimeout(20000);

				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());

				String mensagem = in.readUTF();
				System.out.println("[Servidor] Mensagem recebida de " + socket.getInetAddress().getHostAddress() + ":"
						+ socket.getPort() + "->" + mensagem);

				String resposta = "Olá cliente " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
				out.writeUTF(resposta);

				ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

				while (true) {
					Mensagem m = (Mensagem) inObject.readObject();
					System.out.println("[Servidor] Recebi a pessoa " + p.toString() + " de "
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