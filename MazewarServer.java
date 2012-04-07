import java.util.*;
import java.net.*;
import java.io.*;
import java.io.Serializable;

/*
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
*/

//public class MazewarServer extends JFrame {
public class MazewarServer {

	private ServerSocket socket = null;
	private Server server = null;
	private int port;
        
        private final int mazeWidth = 20;
        private final int mazeHeight = 10;
        private final int mazeSeed = 42;

	private Maze maze = null;
	private OverheadMazePanel overheadPanel = null;

	/*
	private JTable scoreTable = null;
	private static final JTextPane console = new JTextPane();

	public static synchronized void consolePrintLn(String msg) {
		console.setText(console.getText()+msg+"\n");
	}

	public static synchronized void consolePrint(String msg) {
		console.setText(console.getText()+msg);
	}

	public static synchronized void clearConsole() {
		console.setText("");
	}
	*/
        public static void quit() {
                // Put any network clean-up code you might have here.
                // (inform other implementations on the network that you have 
                //  left, etc.)
                System.exit(0);
        }

	public MazewarServer(int port) {
		//super("ECE419 Mazewar Server");
		System.out.println("ECE419 Mazewar server listening...");
		
		port = port;

		// Create the maze
		maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
		assert(maze != null);
		
		// Have the ScoreTableModel listen to the maze to find
		// out how to adjust scores.
		ScoreTableModel scoreModel = new ScoreTableModel();
		assert(scoreModel != null);
		maze.addMazeListener(scoreModel);

		/*
		// TMP REMOTE BOT TO SEE MAZE
		RemoteClient newClient = new RemoteClient("OBSERVER");
		maze.addClient(newClient);

		// Create the panel that will display the maze.
		overheadPanel = new OverheadMazePanel(maze, newClient);
		assert(overheadPanel != null);
		maze.addMazeListener(overheadPanel);

		// Don't allow editing the console from the GUI
		console.setEditable(false);
		console.setFocusable(false);
		console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));

		// Allow the console to scroll by putting it in a scrollpane
		JScrollPane consoleScrollPane = new JScrollPane(console);
		assert(consoleScrollPane != null);
		consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));

		// Create the score table
		scoreTable = new JTable(scoreModel);
		assert(scoreTable != null);
		scoreTable.setFocusable(false);
		scoreTable.setRowSelectionAllowed(false);

		// Allow the score table to scroll too.
		JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
		assert(scoreScrollPane != null);
		scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));
		// Create the layout manager
		GridBagLayout layout = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		getContentPane().setLayout(layout);

		// Define the constraints on the components.
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 3.0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		layout.setConstraints(overheadPanel, c);
		c.gridwidth = GridBagConstraints.RELATIVE;
		c.weightx = 2.0;
		c.weighty = 1.0;
		layout.setConstraints(consoleScrollPane, c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1.0;
		layout.setConstraints(scoreScrollPane, c);

		// Add the components
		getContentPane().add(overheadPanel);
		getContentPane().add(consoleScrollPane);
		getContentPane().add(scoreScrollPane);

		// Pack everything neatly.
		pack();

		// Let the magic begin.
		setVisible(true);
		overheadPanel.repaint();
		this.requestFocusInWindow();
		*/

		try {
			socket = new ServerSocket(port);
			server = new Server(maze, scoreModel);
			boolean listening = true;
			while (listening) {
				ServerThread t = new ServerThread(socket.accept(), server);
				new Thread(t).start();
			}
			socket.close();
		} catch (Exception e) {
			System.out.println("ERROR: mazewar exception! "+e);
			System.exit(-1);
		}
		quit();
        }

        public static void main(String args[]) {
		if (args.length != 1) {
			System.out.println("ERROR: requires port number!");
			System.exit(-1);
		}

		new MazewarServer(Integer.parseInt(args[0]));
	}
}

class Server implements Runnable {

	private static final int MAX_CONNECTIONS = 4; // max # of connections
	private int[] connections = new int[1]; // need data struct to put a lock on it
	private int seq_no = 1; // sequence number
	private Queue<MazePacket> queue = new LinkedList<MazePacket>(); // broadcast queue
	private ArrayList<String> clients = new ArrayList<String>();
	private HashMap<String, RemoteClient> directory = new HashMap<String, RemoteClient>(); // mapping of key to client
	private HashMap<String, ObjectOutputStream> broadcast = new HashMap<String, ObjectOutputStream>(); // mapping of client to outputstream
	private Maze maze = null; // maze object
	private ScoreTableModel scoreModel = null; // score object
	private final Thread thread; // broadcast thread

	public Server(Maze maze, ScoreTableModel scoreModel) {
		this.maze = maze;
		this.scoreModel = scoreModel;
		connections[0] = 0;
		thread = new Thread(this);
		thread.start();
	}

	// broadcast thread
	public void run () {
		while (true) {
			if (queue.peek() != null) {
				MazePacket m = queue.remove();
				if (m.type == MazePacket.MAZE_NULL) {
					continue;
				} else if (m.type == MazePacket.MAZE_CONNECT) {
					addClient(m);
				} else if (m.type == MazePacket.MAZE_DATA && !handle(m)) {
					continue;
				} else if (m.type == MazePacket.MAZE_DISCONNECT) {
					removeClient(m);
				}
				// if it reaches this point, good to broadcast.
				synchronized (clients) {
					m.seq_no = seq_no++;
					for (int i=0; i<clients.size(); ++i) {
						try {
							ObjectOutputStream out = broadcast.get(clients.get(i));
							out.writeObject(m);
						} catch (Exception e) {
							System.out.println("THERE WAS AN ERROR BROADCASTING.. prob cleanup problems");
						}
					}
				}
			}
		}
	}

	public boolean handle(MazePacket m) {
		if (m.ce == null) { return false; } // no action given
		RemoteClient c = directory.get(m.sig.key());
		synchronized(maze) {
			if (m.ce.event == ClientEvent.MOVE_FORWARD) {
				return c.forward();
			} else if (m.ce.event == ClientEvent.MOVE_BACKWARD) {
				return c.backup();
			} else if (m.ce.event == ClientEvent.TURN_LEFT) {
				c.turnLeft();
				return true;
			} else if (m.ce.event == ClientEvent.TURN_RIGHT) {
				c.turnRight();
				return true;
			} else if (m.ce.event == ClientEvent.FIRE) {
				return c.fire();
			} else if (m.ce.event == ClientEvent.DEATH) {
				Client d = directory.get(m.remote_clients.get(0));
				m.p = new DirectedPoint(maze.killClient(d, c));
				return true;
			}
		}
		return false;
	}

	public void enqueue(MazePacket m) {
		synchronized (queue) {
			queue.offer(m);
		}
	}

	public void addClient(MazePacket m) {
		RemoteClient newClient = new RemoteClient(m.sig.key());
		directory.put(m.sig.key(), newClient);
		synchronized(maze) {
			maze.addClient(newClient);
		}
		m.p = new DirectedPoint(newClient.getPoint(), newClient.getOrientation());
		// add myself to client list
		synchronized(clients) {
			if (clients.size() > 0) {
				m.remote_clients = new ArrayList<String>();
				m.current_score = new HashMap<String, Integer>();
				m.current_pos = new HashMap<String, DirectedPoint>();
				for (int i=0; i < clients.size(); ++i) {
					String name = clients.get(i);
					RemoteClient c = directory.get(name);
					m.remote_clients.add(name);
					m.current_score.put(name, scoreModel.getScore(c));
					m.current_pos.put(name, new DirectedPoint(c.getPoint(), c.getOrientation()));
				}
			}
			clients.add(m.sig.name);
		}
	}

	public void removeClient(MazePacket m) {
		synchronized (connections) {
			connections[0]--;
		}
		maze.removeClient(directory.remove(m.sig.key()));
	}

	public boolean addBroadcast(String key, ObjectOutputStream o) {
		// add to broadcast map
		synchronized (broadcast) {
			if (broadcast.containsKey(key) || isFull()) {
				return false;
			}
			broadcast.put(key, o);
			return true;
		}
	}

	public void removeBroadcast(String key) {
		// remove from client list and broadcast map
		synchronized (clients) {
			clients.remove(key);
		}
		synchronized (broadcast) {
			broadcast.remove(key);
		}
	}

	public boolean isFull() {
		synchronized (connections) {
			if (connections[0] < MAX_CONNECTIONS) {
				connections[0]++;
				return false;
			}
			return true;
		}
	}

}

class ServerThread implements Runnable {

	private Socket socket = null;
	private Server server = null;
	private ObjectOutputStream out = null;
	private ObjectInputStream in = null;
	private MazePacket m = null;

	public ServerThread(Socket socket, Server server) {
		this.socket = socket;
		this.server = server;
	}

	public void run() {
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());

			while ((m = (MazePacket) in.readObject()) != null) {
				if (m.sig != null) {
					if (m.type == MazePacket.MAZE_CONNECT) {
						if (!server.addBroadcast(m.sig.key(), out)) {
							out.writeObject(new MazePacket("Server full / Username taken."));
							break;
						}
					} else if (m.type == MazePacket.MAZE_DISCONNECT) {
						// remove myself from broadcast
						server.removeBroadcast(m.sig.key());
						// send last packet to finish handshake
						out.writeObject(m);
						// tell others
						server.enqueue(m);
						break;
					}
					server.enqueue(m);
				}
			}
			System.out.println("Done receiving.");

		} catch (EOFException e) {
			System.out.println("Stream reached EOF unexpectedly");
			// exception thrown, do cleanup
			if (m != null) {
				server.removeBroadcast(m.sig.key());
				m.type = MazePacket.MAZE_DISCONNECT;
				server.enqueue(m);
			}
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("ERROR: exception in ServerThread!");
			// exception thrown, do cleanup
			if (m != null) {
				server.removeBroadcast(m.sig.key());
				m.type = MazePacket.MAZE_DISCONNECT;
				server.enqueue(m);
			}
		}
	}
}

