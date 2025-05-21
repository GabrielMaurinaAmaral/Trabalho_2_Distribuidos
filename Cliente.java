import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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
			InetAddress endereco;
			endereco = InetAddress.getByName(host);

			Socket socket = new Socket(endereco, porta);

			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());

			String resposta = "Olá servidor eu sou " + socket.getInetAddress().getHostAddress() + ":"
					+ socket.getPort();
			out.writeUTF(resposta);

			String mensagem = in.readUTF();
			System.out.println("[Cliente] Mensagem recebida de " + socket.getInetAddress().getHostAddress() + ":"
					+ socket.getPort() + "->" + mensagem);

			ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());

			while (true) {
				Scanner scanner = new Scanner(System.in);
				System.out.print("[Cliente] Digite o número de clientes: ");
				String conteudo = scanner.nextLine();
				scanner.close();
				
				Mensagem m = new Mensagem(host, socket.getInetAddress().getHostAddress(), conteudo);
				outObject.writeObject(m);
			}

			socket.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		System.out.print("[Cliente] Digite o nome de clientes: ");
		String nome = scanner.nextLine();
		scanner.close();

		Thread cliente = new Thread(new Cliente("localhost", 1234, nome));
		cliente.start();
	}
}