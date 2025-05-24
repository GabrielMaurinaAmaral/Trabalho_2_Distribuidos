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
		Socket socket;
		ObjectOutputStream out;

		ClienteInfo(Socket socket, ObjectOutputStream out) {
			this.socket = socket;
			this.out = out;
		}
	}
	
	private static ConcurrentHashMap<String, ClienteInfo> clientesConectados = new ConcurrentHashMap<>();

	public void run() throws IOException {
		ServerSocket conexao = new ServerSocket(porta);
		
		try {
			while (true) {
				System.out.println("[Servidor] Aguardando conexões na porta " + conexao.getLocalPort() + "...");
				//conexao.setSoTimeout(0);
				Socket socket = conexao.accept();
				String idCliente = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
				ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
				clientesConectados.put(idCliente, new ClienteInfo(socket, outObject));

				System.out.println("[Servidor] Conexão aceita: " + idCliente);
				System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());

				Thread cliente = new Thread(new Processador(socket, idCliente, outObject));
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
		private ObjectOutputStream outObject;

		public Processador(Socket socket, String idCliente, ObjectOutputStream outObject) {
			this.socket = socket;
			this.idCliente = idCliente;
			this.outObject = outObject;
		}

		@Override
		public void run() {
			try {
				socket.setSoTimeout(60000);

				ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

				int aux = 1;

				while (aux == 1) {
					Mensagem m = (Mensagem) inObject.readObject();
					System.out.println("[Servidor] Recebi a pessoa " + m.toString() + " de "
							+ socket.getInetAddress().getHostAddress() + ":" + socket.getPort());

					if (m.getComando() == -1) {
						outObject.writeObject(new Mensagem(-1, "Servidor", m.getRemetente(), "Você foi desconectado!"));
						outObject.flush();
						aux = 0;
						break;

					} else if (m.getComando() == 0) {
						for (String id : clientesConectados.keySet()) {
							if (!id.equals(idCliente)) {
								ClienteInfo clienteInfo = clientesConectados.get(id);
								if (m.getDestinatario() != null && id.equals(m.getDestinatario())) {
									clienteInfo.out.writeObject(
											new Mensagem(0, m.getRemetente(), m.getDestinatario(), m.getConteudo()));
									clienteInfo.out.flush();
								}
							}
						}
					} else if (m.getComando() == 1) {
						StringBuilder lista = new StringBuilder();
						for (String id : clientesConectados.keySet()) {
							lista.append(id).append("; ");
						}
						outObject.writeObject(new Mensagem(1, "Servidor", m.getRemetente(), lista.toString()));
						outObject.flush();
					} else if (m.getComando() == 2) {
						for (String id : clientesConectados.keySet()) {
							if (!id.equals(idCliente)) {
								try {
									ClienteInfo clienteInfo = clientesConectados.get(id);
									clienteInfo.out.writeObject(new Mensagem(2, m.getRemetente(), "Todos", m.getConteudo()));
									clienteInfo.out.flush();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
				socket.close();

			} catch (SocketTimeoutException e) {
				System.out.println("[Servidor] Conexão com o cliente " + socket.getInetAddress().getHostAddress() + ":"
						+ socket.getPort() + " encerrada por inatividade.");
				clientesConectados.remove(this.idCliente);

				try {
					outObject.writeObject(new Mensagem(-1, null, null, null));
					outObject.flush();
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				System.out.println("[Servidor] Cliente removido: " + this.idCliente);
				System.out.println("[Servidor] Clientes conectados: " + clientesConectados.keySet());

			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				clientesConectados.remove(this.idCliente);
				try {
					outObject.writeObject(new Mensagem(-1, null, null, null));
					outObject.flush();
				} catch (IOException e) {
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