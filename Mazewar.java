/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/

import java.net.*;
import java.io.*;
import java.util.*;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
import java.io.Serializable;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

        private Maze maze = null;
        private GUIClient guiClient = null;
        private OverheadMazePanel overheadPanel = null;
        private JTable scoreTable = null;
	private ScoreTableModel scoreModel = null;
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

        public static void quit() {
		// Put any network clean-up code you might have here.
		// (inform other implementations on the network that you have 
		//  left, etc.)
		System.exit(0);
        }

	public void magic(GUIClient gui) {
		// add key listener
		this.addKeyListener(gui);

		// Create the panel that will display the maze.
		overheadPanel = new OverheadMazePanel(maze, gui);
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
	}

        public Mazewar (String[] args) {

		try {
			// grab input arguments
			consolePrintLn("ECE419 Mazewar started!");
			String my_name, my_host, ns_host;
			int my_type = MazeSignature.HUMAN_CLIENT, my_port, ns_port, x=0;
			if (args[x].compareTo("-r") == 0) {
				// create a robot !
				my_type = MazeSignature.ROBOT_CLIENT;
				x++;
			}
			my_name = args[x++];
			my_host = InetAddress.getLocalHost().getHostAddress();
			my_port = Integer.parseInt(args[x++]);
			ns_host = args[x++];
			ns_port = Integer.parseInt(args[x++]);

			// check if port is valid
			ServerSocket serverSocket = new ServerSocket(my_port);

			// connect to name service
			Socket socket = new Socket(ns_host, ns_port);
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        MazeSignature sig = new MazeSignature(my_name, my_host, my_port, my_type);
                        MazePacket m = new MazePacket(sig);
                        m.type = MazePacket.MAZE_CONNECT;
                        out.writeObject(m);

                        MazePacket m2 = (MazePacket) in.readObject();
			if (m2 == null) {
				System.out.println("ERROR: failed to receive data from name service.");
				quit();
			} else if (m2.type != MazePacket.MAZE_CONNECT) {
                                System.out.println(m2.err);
                                quit();
                        }

			out.close();
			in.close();
			socket.close();

                        // Create the maze
                        maze = new MazeImpl(new Point(m2.mazeWidth, m2.mazeHeight), m2.mazeSeed);
                        assert(maze != null);
                        
                        // Have the ScoreTableModel listen to the maze to find out how to adjust scores.
                        scoreModel = new ScoreTableModel();
                        assert(scoreModel != null);
                        maze.addMazeListener(scoreModel);

			// create server
			MazeServer server = new MazeServer(maze, scoreModel, sig, ns_host, ns_port);

			// setup ack list
			if (m2.remote_clients != null) {
				for (int i=0; i<m2.remote_clients.size(); i++) {
					String name = m2.remote_clients.get(i).name;
					server.ack_add_client(name);
				}
			}

			// at this point get JOIN_DATA from all the clients returned from connection server.
			if (m2.remote_clients != null) {
				for (int i=0; i<m2.remote_clients.size(); i++) {
					String name = m2.remote_clients.get(i).name;
					String host = m2.remote_clients.get(i).host;
					Integer port = m2.remote_clients.get(i).port;

					try {
						socket = new Socket(host, port);
						out = new ObjectOutputStream(socket.getOutputStream());
						in = new ObjectInputStream(socket.getInputStream());

						m = new MazePacket(sig);
						m.type = MazePacket.JOIN_DATA;
						out.writeObject(m);

						MazePacket m3 = (MazePacket) in.readObject();

						if (m3.type != MazePacket.JOIN_DATA) {
							System.out.println("ERROR: did not receive join data back from client..");
							server.ack_rm_client(name);
							continue;
						}

						// get max seq_no
						server.set_max_seq_no(m3.seq_no);
						// add to server broadcast
						server.client_add(m3.sig.key(), out);
						// start thread
						ServerThread t = new ServerThread(socket, server, m3.sig, out, in);
						new Thread(t).start();
					} catch (Exception e) {
						System.out.println("ERROR: connection failed with a client during setup");
						server.ack_rm_client(name);
					}
					
				}
			}

			// add my own MazePacket.MAZE_CONNECT to queue and payload
			MazePacket connect = new MazePacket(sig);
			connect.type = MazePacket.MAZE_CONNECT;
			connect.seq_no = server.get_seq_no();
			server.queue_add(connect);
			server.payload_add(connect.key(), connect);

			// send JOIN_REQ to all clients!
			m = new MazePacket(sig);
			m.type = MazePacket.JOIN_REQ;
			m.seq_no = server.get_seq_no();
			server.client_broadcast(m);

			// start client thread
                        ClientThread ct = new ClientThread(this, server);
                        new Thread(ct).start();

			// poll for new connections
			boolean listening = true;
			while (listening) {
				ServerThread st = new ServerThread(serverSocket.accept(), server);
				new Thread(st).start();
			}


                } catch (Exception e) {
                        System.out.println(e);
                }
		quit();
        }
        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String[] args) {
                if (args.length < 4) {
                        System.out.println("ERROR: run as Mazewar [-r] <name> <port> <naming service hostname> <naming service port>");
                        System.exit(-1);
                }
                /* Create the GUI */
                new Mazewar(args);
        }
}

class ServerThread implements Runnable {

	private Socket socket = null;
	private MazeServer server = null;
	private MazeSignature client_sig = null;
	private int client_type = MazeSignature.HUMAN_CLIENT;
	ObjectOutputStream out = null;
	ObjectInputStream in = null;
	MazePacket m = null;

	ServerThread(Socket socket, MazeServer server) {
		this.socket = socket;
		this.server = server;
	}

	ServerThread(Socket socket, MazeServer server, MazeSignature client_sig, ObjectOutputStream out, ObjectInputStream in) {
		this.socket = socket;
		this.server = server;
		this.client_sig = client_sig;
		this.out = out;
		this.in = in;
	}

	public void run () {
		try {
			if (this.out == null) {
				this.out = new ObjectOutputStream(socket.getOutputStream());
			}
			if (this.in == null) {
				this.in = new ObjectInputStream(socket.getInputStream());
			}
			while ((m = (MazePacket) in.readObject()) != null) {
				if (m.sig != null) {
					if (m.type == MazePacket.MAZE_NULL) {
						continue;
					} else if (m.type == MazePacket.MAZE_CONNECT) {
						server.payload_add(m.key(), m);
					} else if (m.type == MazePacket.MAZE_DISCONNECT) {
						// remove from broadcast and do cleanup
						server.client_rm(m.sig.key());
						server.queue_rm_client(client_sig.key());
						server.ack_rm_client(m.sig.key());
						// insert into beginning of queue
						m.seq_no = -1;
						server.queue_add(m);
						server.payload_add(m.key(), m);
						continue;
					} else if (m.type == MazePacket.MAZE_DATA) {
						server.payload_add(m.key(), m);
					} else if (m.type == MazePacket.MAZE_REQ) {
						// if not joined yet and m.seq_no <= seq_no, ignore?
						server.queue_add(m);
					} else if (m.type == MazePacket.MAZE_ACK) {
						server.ack_add(m.ack_pkt, m.sig.key());
					} else if (m.type == MazePacket.JOIN_DATA) {
						MazePacket mp = new MazePacket(server.get_sig());
						mp.type = MazePacket.JOIN_DATA;
						mp.seq_no = server.get_next_seq_no();
						client_type = m.sig.type();
						server.client_add(m.sig.key(), out);
						server.client_send(m.sig.key(), mp);
						client_sig = new MazeSignature(m.sig);
						continue;
					} else if (m.type == MazePacket.JOIN_REQ) {
						server.queue_add(m);
					} else if (m.type == MazePacket.JOIN_ACK) {
						String name = m.sig.key();
						// create client and insert to maze
						if (m.sig.type() == MazeSignature.HUMAN_CLIENT) {
							RemoteClient remoteClient = new RemoteClient(name);
							server.maze_addClient(remoteClient, m.current_pos.get(name));
							server.score_setScore(remoteClient, m.current_score.get(name));
							server.directory_put(name, remoteClient);
							server.ack_add(m.ack_pkt, name);
							Mazewar.consolePrintLn("Client \""+name+"\" has joined the game.");
						} else if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
							RobotClient robotClient = new RobotClient(name);
							server.maze_addClient(robotClient, m.current_pos.get(name));
							server.score_setScore(robotClient, m.current_score.get(name));
							server.directory_put(name, robotClient);
							server.ack_add(m.ack_pkt, name);
							Mazewar.consolePrintLn("Robot client \""+name+"\" activated.");
						}
					}
					server.set_max_seq_no(m.seq_no);
				}
			}
			System.out.println("ServerThread done receiving.");

		} catch (EOFException e) {
			System.out.println("ERROR: ["+client_sig.key()+"] Stream reached EOF unexpectedly");
		} catch (Exception e) {
			System.out.println("ERROR: exception in server thread!");
		}
		// remove from broadcast and do cleanup
		server.client_rm(client_sig.key());
		server.queue_rm_client(client_sig.key());
		server.ack_rm_client(client_sig.key());
		server.maze_removeClient(server.directory_rm(client_sig.key()));
		server.nameservice_rm(client_sig);
		if (client_type == MazeSignature.HUMAN_CLIENT) {
			Mazewar.consolePrintLn("Client \""+client_sig.key()+"\" has left the game.");
		} else if (client_type == MazeSignature.ROBOT_CLIENT) {
			Mazewar.consolePrintLn("Robot client \""+client_sig.key()+"\" deactivated.");
		}
	}

}

class ClientThread implements Runnable {

	private MazeServer server = null;
	private Mazewar mazewar = null;

        public ClientThread(Mazewar mazewar, MazeServer server) {
		this.mazewar = mazewar;
		this.server = server;
        }

        public void run() {
                try {
			// Let everything settle
			Thread.sleep(100);
			MazePacket m = null;
                        while (server.running) {
				// Retrieve packet from the queue
				m = server.queue_ready();
				if (m != null) {
					if (m.type == MazePacket.MAZE_DATA) {
						if (m.sig.key().compareTo(server.get_sig().key()) == 0 && m.ce.event != ClientEvent.DEATH) {
							// broadcast to all others!
							server.client_broadcast(m);
						}
						handle(m);
					} else if (m.type == MazePacket.MAZE_CONNECT) {
						if (m.sig.key().compareTo(server.get_sig().key()) == 0) {
							if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
								// if i'm a robot
								RobotClient robotClient = new RobotClient(server);
								server.maze_addClient(robotClient);
								server.directory_put(server.get_sig().name, robotClient);
								// broadcast to others
								MazePacket mp = new MazePacket(server.get_sig());
								mp.type = MazePacket.MAZE_CONNECT;
								mp.p = new DirectedPoint(robotClient.getPoint(), robotClient.getOrientation());
								mp.seq_no = m.seq_no;
								server.client_broadcast(mp);
							} else {
								// Create the GUIClient
								GUIClient guiClient = new GUIClient(server);
								server.maze_addClient(guiClient);
								server.directory_put(server.get_sig().name, guiClient);
								// broadcast to others
								MazePacket mp = new MazePacket(server.get_sig());
								mp.type = MazePacket.MAZE_CONNECT;
								mp.p = new DirectedPoint(guiClient.getPoint(), guiClient.getOrientation());
								mp.seq_no = m.seq_no;
								server.client_broadcast(mp);
								// start the magic
								mazewar.magic(guiClient);
							}
						} else {
							if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
								// if it's a robot
								String name = m.sig.key();
								RobotClient robotClient = new RobotClient(name);
								server.maze_addClient(robotClient, m.p);
								server.ack_add_client(name);
								server.directory_put(name, robotClient);
								Mazewar.consolePrintLn("Robot client \""+name+"\" activated.");
							} else {
								// else, add remote client to game
								String name = m.sig.key();
								RemoteClient remoteClient = new RemoteClient(name);
								server.maze_addClient(remoteClient, m.p);
								server.ack_add_client(name);
								server.directory_put(name, remoteClient);
								Mazewar.consolePrintLn("Client \""+name+"\" has joined the game.");
							}
						}
					} else if (m.type == MazePacket.MAZE_DISCONNECT) {
						// let server thread take care of it
						/*
						server.maze_removeClient(server.directory_rm(m.sig.key()));
						if (m.sig.type() == MazeSignature.HUMAN_CLIENT) {
							Mazewar.consolePrintLn("Client \""+m.sig.key()+"\" has left the game.");
						} else if (m.sig.type() == MazeSignature.ROBOT_CLIENT) {
							Mazewar.consolePrintLn("Robot client \""+m.sig.key()+"\" deactivated.");
						}*/
					}
                                } else {
					// Process waiting requests
					server.queue_process();
				}
				// sleep 50ms to slow it down
				Thread.sleep(50);
                        }
                        System.out.println("ClientThread done processing.");

                } catch (Exception e) {
                        System.out.println("Error received");
                        System.out.println(e);
                }
		mazewar.quit();
        }


        public boolean handle(MazePacket m) {
                if (m.ce == null) { return false; } // no action given
                Client c = server.directory_get(m.sig.key());
		if (m.ce.event == ClientEvent.MOVE_FORWARD) {
			return server.maze_forward(c);
		} else if (m.ce.event == ClientEvent.MOVE_BACKWARD) {
			return server.maze_backup(c);
		} else if (m.ce.event == ClientEvent.TURN_LEFT) {
			server.maze_turnLeft(c);
			return true;
		} else if (m.ce.event == ClientEvent.TURN_RIGHT) {
			server.maze_turnRight(c);
			return true;
		} else if (m.ce.event == ClientEvent.FIRE) {
			return server.maze_fire(c);
		} else if (m.ce.event == ClientEvent.DEATH) {
			Client d = server.directory_get(m.remote_clients.get(0).key());
			server.maze_killClient(d, c, m.p);
			if (m.sig.key().compareTo(server.get_sig().key()) == 0) {
				m.p = new DirectedPoint(c.getPoint(), c.getOrientation());
				// broadcast to all others!
				server.client_broadcast(m);
			}
			return true;
		}
                return false;
        }
}
