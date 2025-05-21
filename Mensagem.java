import java.io.Serializable;
import java.time.LocalDateTime;

public class Mensagem implements Serializable {
    private String remetente;
    private String destinatario; // null para broadcast
    private String conteudo;
    private LocalDateTime horario;

    public Mensagem(String remetente, String destinatario, String conteudo) {
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.conteudo = conteudo;
        this.horario = LocalDateTime.now();
    }

    public String getRemetente() { return remetente; }
    public String getDestinatario() { return destinatario; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getHorario() { return horario; }

    @Override
    public String toString() {
        String destino = (destinatario == null) ? "Todos" : destinatario;
        return "[" + horario.toString() + "] " + remetente + " -> " + destino + ": " + conteudo;
    }
}