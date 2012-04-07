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

public class MazeServer {

	public LinkedList<MazePacket> queue = new LinkedList<MazePacket>();
	public HashMap<String, MazePacket> payload = new HashMap<String, MazePacket>();
	public HashMap<String, HashSet<String>> ack = new HashMap<String, HashSet<String>>();
	public ArrayList<String> processed_requests = new ArrayList<String>();
	public HashSet<String> clients = new HashSet<String>();
        private HashMap<String, Client> directory = new HashMap<String, Client>();
	private HashMap<String, ObjectOutputStream> client_connections = new HashMap<String, ObjectOutputStream>();
        private Maze maze = null;
	private ScoreTableModel scoreModel = null;
	private MazeSignature sig = null;
	private String ns_host = "";
	private Integer ns_port = 0;
	private Object seq_no_lock = new Object();
	public int seq_no = 0;
	
	public boolean running = true;

	MazeServer (Maze maze, ScoreTableModel scoreModel, MazeSignature sig, String ns_host, Integer ns_port) {
		this.maze = maze;
		this.scoreModel = scoreModel;
		this.sig = new MazeSignature(sig);
		this.ns_host = new String(ns_host);
		this.ns_port = ns_port;
	}

	public MazeSignature get_sig () {
		return sig;
	}

	public void client_add(String key, ObjectOutputStream out) {
		synchronized (client_connections) {
			client_connections.put(key, out);
		}
	}

	public void client_send(String key, MazePacket m) {
		synchronized (client_connections) {
			try {
				ObjectOutputStream out = client_connections.get(key);
				if (out != null) {
					out.writeObject(m);
					out.reset();
				}
			} catch (Exception e) {
				System.out.println("ERROR: exception in sending to client.");
			}
		}
	}

	public void client_rm (String key) {
		synchronized (client_connections) {
			client_connections.remove(key);
		}
	}

	public void client_broadcast(MazePacket m) {
		synchronized (client_connections) {
			for (Map.Entry<String, ObjectOutputStream> entry : client_connections.entrySet()) {
				try {
					ObjectOutputStream out = entry.getValue();
					out.writeObject(m);
					out.reset();
				} catch (Exception e) {
					System.out.println("ERROR: exception in broadcast. Probably cleanup error.");
				}
			}
		}
	}

	public void set_max_seq_no (int sn) {
		synchronized (seq_no_lock) {
			if (this.seq_no < sn) {
				this.seq_no = sn;
			}
		}
	}

	public int get_seq_no () {
		synchronized (seq_no_lock) {
			return seq_no;
		}
	}

	public int get_next_seq_no () {
		synchronized (seq_no_lock) {
			++seq_no;
			return seq_no;
		}
	}

	public void queue_add (MazePacket m) {
		synchronized (queue) {
			int i = 0;
			while (i < queue.size() && queue.get(i).lesserThan(m)) {
				++i;
			}
			queue.add(i, m);
			payload_add(m.key(), null);
			if (m.sig.key().compareTo(sig.key()) == 0) {
				ack_add(m.key(), null);
			}
		}
	}

	public MazePacket queue_peek (int index) {
		synchronized (queue) {
			if (index < queue.size()) {
				return queue.get(index);
			}
		}
		return null;
	}

	public MazePacket queue_ready () {
		synchronized (queue) {
			MazePacket m = queue.peekFirst();
			if (m != null) {
				if (m.type == MazePacket.MAZE_DISCONNECT) {
					// someone has disconnected
					return queue_rm(m);
				} else if (m.sig.key().compareTo(sig.key()) == 0) {
					// it's my own, check acks
					if (ack_check(m.key())) {
						return queue_rm(m);
					}
				} else {
					// check if payload is ready
					if (payload_check(m.key())) {
						return queue_rm(m);
					}
				}
			}
			return null;
		}
	}

	public void queue_process() {
		synchronized (queue) {
			int i=0;
			while (i < queue.size()) {
				MazePacket m = queue.get(i);
				if (m != null) {
					if (m.sig.key().compareTo(sig.key()) == 0) {
						return;
					}
					if (m.type == MazePacket.MAZE_DISCONNECT) {
						return;
					}
					if (!processed_requests.contains(m.key())) {
						MazePacket mp = new MazePacket(sig);
						if (m.type == MazePacket.JOIN_REQ) {
							mp.current_score = new HashMap<String, Integer>();
							mp.current_pos = new HashMap<String, DirectedPoint>();

							// SEND CURRENT POSITION AND SCORE TO CLIENT!
							Client myself = directory_get(sig.key());
							mp.type = MazePacket.JOIN_ACK;
							mp.ack_pkt = m.key();
							mp.current_score.put(sig.key(), score_getScore(myself));
							mp.current_pos.put(sig.key(), new DirectedPoint(myself.getPoint(), myself.getOrientation()));
						} else if (m.type == MazePacket.MAZE_REQ) {
							// send ACK
							mp.type = MazePacket.MAZE_ACK;
							mp.ack_pkt = m.key();
							mp.seq_no = get_next_seq_no();
						}
						client_send(m.sig.key(), mp);
						processed_requests.add(m.key());
					}
				}
				++i;
			}
		}
	}

	public MazePacket queue_rm (MazePacket mp) {
		synchronized (queue) {
			int index = queue.indexOf(mp);
			if (index < 0) {
				return null;
			}
			MazePacket m = queue.remove(index);
			processed_requests.remove(m.key());
			ack_rm(m.key());
			return payload_rm(m.key());
		}
	}

	public void queue_rm_client (String key) {
		ArrayList<MazePacket> cleanup = new ArrayList<MazePacket>();
		synchronized (queue) {
			for (MazePacket m : queue) {
				if (m.sig.key().compareTo(key) == 0) {
					cleanup.add(m);
				}
			}
		}
		for (MazePacket m : cleanup) {
			queue_rm(m);
		}
	}

	public void payload_add (String key, MazePacket m) {
		synchronized (payload) {
			if (m == null) {
				payload.put(key, null);
			} else if (m.type == MazePacket.MAZE_DISCONNECT) {
				payload.put(key, m);
			} else if (payload.containsKey(key)) {
				payload.put(key, m);
			}
		}
	}

	public boolean payload_check (String key) {
		synchronized (payload) {
			MazePacket m = payload.get(key);
			if (m == null) {
				return false;
			}
			return true;
		}
	}

	public MazePacket payload_rm (String key) {
		synchronized (payload) {
			return payload.remove(key);
		}
	}

	public void ack_add (String key, String client) {
		synchronized (ack) {
			if (ack.containsKey(key)) {
				ack.get(key).add(client);
			} else {
				HashSet<String> ack_clients = new HashSet<String>();
				if (client != null) {
					ack_clients.add(client);
				}
				ack.put(key, ack_clients);
			}
		}
	}

	public boolean ack_check (String key) {
		synchronized (ack) {
			if (ack.containsKey(key)) {
				HashSet<String> acks = ack.get(key);
				synchronized (clients) {
					for (String c : clients) {
						if (!acks.contains(c)) {
							return false;
						}
					}
				}
				return true;
			}
			return false;
		}
	}

	public void ack_add_client (String client) {
		synchronized (clients) {
			clients.add(client);
		}
	}

	public void ack_rm (String key) {
		synchronized (ack) {
			ack.remove(key);
		}
	}

	public void ack_rm_client (String client) {
		synchronized (ack) {
			for (Map.Entry<String, HashSet<String>> entry : ack.entrySet()) {
				if (entry.getValue().contains(client)) {
					entry.getValue().remove(client);
				}
			}
		}
		synchronized (clients) {
			clients.remove(client);
		}
	}

	public Client directory_get(String key) {
		synchronized (directory) {
			return directory.get(key);
		}
	}

	public Client directory_rm(String key) {
		synchronized (directory) {
			return directory.remove(key);
		}
	}
	
	public void directory_put(String key, Client c) {
		synchronized (directory) {
			directory.put(key, c);
		}
	}

	public void maze_addClient(Client c) {
		synchronized (maze) {
			if (c != null) {
				maze.addClient(c);
			}
		}
	}

	public void maze_addClient(Client c, DirectedPoint dp) {
		synchronized (maze) {
			if (c != null && dp != null) {
				maze.addClient(c, dp);
			}
		}
	}

	public void maze_removeClient(Client c) {
		synchronized (maze) {
			if (c != null) {
				maze.removeClient(c);
			}
		}
	}

	public void maze_killClient(Client d, Client c, DirectedPoint dp) {
		synchronized (maze) {
			if (d != null && c != null) {
				if (dp == null) {
					maze.killClient(d, c);
				} else {
					maze.killClient(d, c, dp);
				}
			}
		}
	}

	public boolean maze_getClientFired(Client c) {
		synchronized (maze) {
			if (c != null) {
				return maze.getClientFired(c);
			}
		}
		return false;
	}

	public boolean maze_forward(Client c) {
		synchronized (maze) {
			return c.forward();
		}
	}

	public boolean maze_backup(Client c) {
		synchronized (maze) {
			return c.backup();
		}
	}

	public void maze_turnLeft(Client c) {
		synchronized (maze) {
			c.turnLeft();
		}
	}

	public void maze_turnRight(Client c) {
		synchronized (maze) {
			c.turnRight();
		}
	}

	public boolean maze_fire(Client c) {
		synchronized (maze) {
			return c.fire();
		}
	}

	public Integer score_getScore(Client c) {
		synchronized (scoreModel) {
			if (c != null) {
				return scoreModel.getScore(c);
			}
		}
		return 0;
	}

	public void score_setScore(Client c, Integer score) {
		synchronized (scoreModel) {
			if (c != null) {
				scoreModel.setScore(c, score);
			}
		}
	}

	public void nameservice_rm (MazeSignature ms) {
		try {
			// send to naming server
			Socket socket = new Socket(ns_host, ns_port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			MazePacket m = new MazePacket(ms);
			m.type = MazePacket.MAZE_DISCONNECT;
			out.writeObject(m);

			MazePacket m2 = (MazePacket) in.readObject();
			if (m2 == null) {
				System.out.println("ERROR: ["+sig.key()+"] failed to send disconnect to name service.");
			} else if (m2.type != MazePacket.MAZE_DISCONNECT) {
				System.out.println("ERROR: ["+sig.key()+"] failed to complete disconnect handshake with name service.");
			}

			out.close();
			in.close();
			socket.close();
		} catch (Exception e) {
			System.out.println("ERROR: ["+sig.key()+"] exception thrown while disconnecting.");
		}
	}

	public void disconnect () {
		// contact name service
		nameservice_rm(sig);
		// broadcast to all clients
		MazePacket m = new MazePacket(sig);
		m.type = MazePacket.MAZE_DISCONNECT;
		client_broadcast(m);
		// exit
		running = false;
	}
}
