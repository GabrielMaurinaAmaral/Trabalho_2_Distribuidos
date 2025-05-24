import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Mensagem implements Serializable {
    private int comando;
    private String remetente;
    private String destinatario;
    private String conteudo;
    private LocalDateTime horario;

    public Mensagem(int comando, String remetente, String destinatario, String conteudo) {
        this.comando = comando;
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.conteudo = conteudo;
        this.horario = LocalDateTime.now();
    }

    public int getComando() {
        return comando;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    @Override
    public String toString() {
        String destino = (destinatario == null) ? "Todos" : destinatario;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return "[" + horario.format(formatter) + "] " + remetente + ":" + comando + " -> " + destino + ":" + conteudo;
    }
}