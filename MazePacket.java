import java.io.Serializable;
import java.util.*;

class MazeSignature implements Serializable {
	public static final int HUMAN_CLIENT	= 0;
	public static final int ROBOT_CLIENT	= 1;

	public String name = "";
	public String host = "";
	public Integer port = 0;
	public Integer type = MazeSignature.HUMAN_CLIENT;

	public MazeSignature(String name, String host, Integer port) {
		this.name = name;
		this.host = host;
		this.port = port;
	}

	public MazeSignature(String name, String host, Integer port, Integer type) {
		this.name = name;
		this.host = host;
		this.port = port;
		this.type = type;
	}

	public MazeSignature(String name) {
		this.name = name;
	}

	public MazeSignature(MazeSignature ms) {
		this.name = ms.name;
		this.host = ms.host;
		this.port = ms.port;
		this.type = ms.type;
	}

	// Unique key for a client user
	public String key () {
		return name;
	}

	public Integer type () {
		return type;
	}

	public String toString() {
		return name+":"+host+":"+port;
	}

}

public class MazePacket implements Serializable {

	public static final int MAZE_NULL	= 0;
	public static final int MAZE_CONNECT	= 1;
	public static final int MAZE_DISCONNECT	= 2;
	public static final int MAZE_DATA	= 3;
	public static final int MAZE_REQ	= 4;
	public static final int MAZE_ACK	= 5;
	public static final int JOIN_DATA	= 6;
	public static final int JOIN_REQ	= 7;
	public static final int JOIN_ACK	= 8;

	public int type = MazePacket.MAZE_NULL;

	public int seq_no = -1; // sequence number, -1 is default/failure

	public int mazeWidth = 20;
	public int mazeHeight = 10;
	public long mazeSeed = 42; // values of the maze

	public MazeSignature sig = null; // name, host, and port

	public ClientEvent ce = null; // client event

	public DirectedPoint p = null; // point & direction of client

	public String ack_pkt = ""; // packet that is being acknowledged

	public String err = ""; // error message

	public ArrayList<MazeSignature> remote_clients = null; // list of client data for other connected players
	public HashMap<String, Integer> current_score = null; // map of client name to current points for initialization
	public HashMap<String, DirectedPoint> current_pos = null; // map of client name to current position in maze

	public MazePacket () {}

	public MazePacket (String err_msg) {
		this.err = err_msg;
	}

	public MazePacket (String name, String host, Integer port) {
		this.sig = new MazeSignature(name, host, port);
	}

	public MazePacket (MazeSignature ms) {
		this.sig = new MazeSignature(ms);
	}

	public String key() {
		return Integer.toString(seq_no)+':'+sig.key();
	}

	public boolean lesserThan (MazePacket m) {
		if (seq_no < m.seq_no) {
			return true;
		}
		if (seq_no == m.seq_no && sig.key().compareTo(m.sig.key()) < 0) {
			return true;
		}
		return false;
	}
}
