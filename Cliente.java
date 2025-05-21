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

public class Cliente implements Runnable{

	private int porta;
	private String host;
	private String nome_cliente;

	public Cliente(String host, int porta) {
		this.host = host;
		this.porta = porta;
	}

	public Cliente(String host, int porta, String nome_cliente) {
		this.host = host;
		this.porta = porta;
		this.nome_cliente = nome_cliente;
	}

	public void run(){

		try {
			//Dados dos servidor
			InetAddress endereco;
			endereco = InetAddress.getByName(host);

			//Passo 2: Conectar o socket a porta do servidor
			Socket socket = new Socket(endereco, porta);

			//Passo 3: Cria canais de comunicação (streams) de entrada e saída
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			
			//Monta mensagem 
			String resposta = "Olá servidor eu sou " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort(); 

			out.writeUTF(resposta); // Envia a mensagem

			String mensagem = in.readUTF(); //Bloqueia até que uma mensagem seja recebida

			// Processa a mensagem
			System.out.println("[Cliente] Mensagem recebida de " +  socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "->" + mensagem);

			//Cria um canal de comunicação para transimitir um objeto ao servidor
			ObjectOutputStream outObject = new ObjectOutputStream(socket.getOutputStream());
		
			outObject.writeObject(p); // Envia a mensagem contendo o objeto
			
			// Passo 4 : Fechar o socket
			socket.close();
				
		}catch(IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) 
	{
		Scanner scanner = new Scanner(System.in);
		System.out.print("[Cliente] Digite o número de clientes: ");
		String nome = scanner.nextLine();
		scanner.close();
			
		Thread cliente = new Thread(new Cliente("localhost", 1234, nome));			
		cliente.start();
	}
}
