import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Servidor{

	private int porta;
	private ServerSocket conexao;
	Socket socket;

	public Servidor(int porta) {
		this.porta = porta;
	}

	public void run() throws IOException{

		// Passo 1 : criar socket de conexão
		conexao = new ServerSocket(porta);
		//Informa a porta onde o servidor vai “ouvir” conexões
		//O socket ainda não está conectado a ninguém.
		
		try {
			while(true) {
				System.out.println("[Servidor] Aguardando conexões na porta " + conexao.getLocalPort() + "...");

				conexao.setSoTimeout(30000); // 30 segundos de inatividade para leitura
		
				//Passo 2: aguarda conexão do cliente
				socket = conexao.accept(); 
				//- O método accept() bloqueia o programa até que um cliente tente se conectar.
				//- Quando alguém se conecta, retorna um Socket específico para aquele cliente.
				//- Esse Socket é usado para se comunicar com aquele cliente individualmente.
		
				
				System.out.println("[Servidor] Conexão aceita ..." + socket.getLocalPort());
				
				Thread cliente = new Thread(new Processador(socket));
				cliente.start();
			
			}
		}catch(SocketTimeoutException e) {
			//Passo 6: fecha o socket de conexão
			System.out.println("[Servidor] Conexão encerrada por inatividade.");
			conexao.close();
		}		
	}
	
	class Processador implements Runnable{
		private Socket socket;
		DataInputStream in;
		DataOutputStream out;
		
		public Processador(Socket socket) {
			this.socket = socket;
		}
		
		@Override
		public void run() {
			try {
				
				socket.setSoTimeout(20000); // 20 segundos de inatividade para leitura
				
				//Passo 3: Cria canais de comunicação (streams) de entrada e saída com o cliente
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				
				//Passo 4: Realiza comunicação
				String mensagem = in.readUTF(); // Lê a mensagem (bloqueia até que uma mensagem seja recebida)
				
				//Processa a mensagem recebida do cliente
				System.out.println("[Servidor] Mensagem recebida de " +  socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "->" + mensagem);
				
				//Constroi a mensagem de resposta
				String resposta = "Olá cliente " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort(); 
				
				out.writeUTF(resposta); // Envia a mensagem ao cliente
				
				//Cria um canal de comunicação para receber objetos
				ObjectInputStream inObject = new ObjectInputStream(socket.getInputStream());
				
				Pessoa p = (Pessoa) inObject.readObject(); // Lê o objeto contido na mensagem (bloqueia até que uma mensagem seja recebida)
				
				System.out.println("[Servidor] Recebi a pessoa " + p.toString() + " de " +  socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
				
				//Passo 5: fecha o socket com o cliente
				socket.close();
			
			}catch(SocketTimeoutException e) {
				System.out.println("[Servidor] Conexão com o cliente "+socket.getInetAddress().getHostAddress() +":"+ socket.getPort()  +" encerrada por inatividade.");
			    try {
					socket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}catch(IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
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
