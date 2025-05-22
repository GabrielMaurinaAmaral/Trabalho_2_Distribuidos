import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
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

	public void run() {

		try {
			InetAddress endereco = InetAddress.getByName(host);
			Socket socket = new Socket(endereco, porta);

			ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());

			Scanner scanner = new Scanner(System.in);

			while (true) {
				System.out.print("[Cliente/" + this.nomeUsuario + ": ");
				String conteudo = scanner.nextLine();
				String[] palavras = conteudo.split(":");

				if (conteudo.trim().equalsIgnoreCase("/sair")) {
					break;
				}

				else if (palavras[0].trim().equalsIgnoreCase("/private")) {
					Mensagem m = new Mensagem(0, nomeUsuario, palavras[1], palavras[2]);
					outObject.writeObject(m);
					outObject.flush();
				} 

				else if (conteudo.trim().equalsIgnoreCase("/usuarios")) {
					Mensagem m = new Mensagem(1, nomeUsuario, null, null);
					outObject.writeObject(m);
					outObject.flush();
				}
				
				else {
					Mensagem m = new Mensagem(2, nomeUsuario, "Todos", conteudo);
					outObject.writeObject(m);
					outObject.flush();
				}
			}
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			System.out.println("[Cliente] Conexão encerrada.");
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