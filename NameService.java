import java.util.*;
import java.net.*;
import java.io.*;
import java.io.Serializable;

public class NameService {

	private ServerSocket serverSocket = null;
	private static HashMap<String, MazeSignature> clientMap = new HashMap<String, MazeSignature>();
	private static final int MAX_CLIENT_CONNECTIONS = 4; // max # of client connections
	private static final int MAX_ROBOT_CONNECTIONS = 4; // max # of robot connections
	private int client_connections = 0; // current # of client connections
	private int robot_connections = 0; // current # of robot connections

	public static void map_toString() {
		for (Map.Entry<String, MazeSignature> entry : clientMap.entrySet()) {
			String key = entry.getKey();
			MazeSignature value = entry.getValue();
			String type = new String("[HUMAN]");
			if (value.type() == MazeSignature.ROBOT_CLIENT) {
				type = new String("[ROBOT]");
			}
			System.out.println("   "+type+" - "+value.toString());
		}
	}

	public NameService(int port) {
		System.out.println("ECE419 Mazewar Name Service");
		try {
			serverSocket = new ServerSocket(port);
		} catch (Exception e) {
			System.out.println("ERROR: Couldn't set up Naming Service. (port taken)");
			System.exit(-1);
		}

		boolean listening = true;
		while (listening) {
			try {
				Socket socket = serverSocket.accept();
				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

				MazePacket m = null;

				if ((m = (MazePacket) in.readObject()) != null) {
					if (m.sig != null) {
						if (m.type == MazePacket.MAZE_CONNECT) {
							System.out.println("-- CONNECT --");
							String name = m.sig.key();
							if (!clientMap.containsKey(name)) {
								if (m.sig.type() == MazeSignature.HUMAN_CLIENT) {
									if (client_connections < MAX_CLIENT_CONNECTIONS) {
										client_connections++;
									} else {
										MazePacket mp = new MazePacket("ERROR: server is full ("+client_connections+" players in game).");
										out.writeObject(mp);
										continue;
									}
								} else if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
									if (robot_connections < MAX_ROBOT_CONNECTIONS) {
										robot_connections++;
									} else {
										MazePacket mp = new MazePacket("ERROR: too many robots! ("+robot_connections+" robots in game).");
										out.writeObject(mp);
										continue;
									}
								}
								MazePacket mp = new MazePacket();
								mp.type = MazePacket.MAZE_CONNECT;
								mp.remote_clients = new ArrayList<MazeSignature>();
								for (Map.Entry<String, MazeSignature> entry : clientMap.entrySet()) {
									mp.remote_clients.add(entry.getValue());
								}
								out.writeObject(mp);
								clientMap.put(name, m.sig);
								map_toString();
							} else {
								MazePacket mp = new MazePacket("ERROR: name is taken.");
								out.writeObject(mp);
							}
						} else if (m.type == MazePacket.MAZE_DISCONNECT) {
							String name = m.sig.key();
							if (clientMap.containsKey(name)) {
								System.out.println("-- DISCONNECT --");
								if (m.sig.type() == MazeSignature.HUMAN_CLIENT) {
									client_connections--;
								} else if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
									robot_connections--;
								}
								clientMap.remove(name);
								map_toString();
							}
							MazePacket mp = new MazePacket();
							mp.type = MazePacket.MAZE_DISCONNECT;
							out.writeObject(mp);
						}
					}
				}
				out.close();
				in.close();
				socket.close();
			} catch (Exception e) {
				System.out.println("ERROR: name service exception! "+e);
			}
		}
		try {
			serverSocket.close();
		} catch (Exception e) {
			System.out.println("ERROR: failed to cleanup Naming Service..");
		}
		System.exit(-1);
        }

        public static void main(String args[]) {
		if (args.length != 1) {
			System.out.println("ERROR: requires port number!");
			System.exit(-1);
		}

		new NameService(Integer.parseInt(args[0]));
	}
}

