import java.io.Serializable;
import java.util.*;

class MazeSignature implements Serializable {
	public String name = "";
	public String host = "";
	public Integer port = 0;

	public MazeSignature(String name, String host, Integer port) {
		this.name = name;
		this.host = host;
		this.port = port;
	}

	public MazeSignature(String name) {
		this.name = name;
	}

	// Unique key for a client user
	public String key () {
		return name;
	}
}

public class MazePacket implements Serializable {

	public static final int MAZE_NULL	= 0;
	public static final int MAZE_CONNECT	= 1;
	public static final int MAZE_DISCONNECT	= 2;
	public static final int MAZE_DATA	= 3;

	public int type = MazePacket.MAZE_NULL;

	public int seq_no = -1; // sequence number, -1 is default/failure

	public int mazeWidth = 20;
	public int mazeHeight = 10;
	public long mazeSeed = 42; // values of the maze

	public MazeSignature sig = null; // name, host, and port

	public ClientEvent ce = null; // client event

	public DirectedPoint p = null; // point & direction of client

	public String err = ""; // error message

	public ArrayList<String> remote_clients = null; // list of client names for other connected players
	public HashMap<String, Integer> current_score = null; // map of client name to current points for initialization
	public HashMap<String, DirectedPoint> current_pos = null; // map of client name to current position in maze

	public MazePacket () {}

	public MazePacket (String err_msg) {
		this.err = err_msg;
	}
}
