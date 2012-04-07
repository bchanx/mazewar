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

        public Mazewar (String[] args) {

		try {
			consolePrintLn("ECE419 Mazewar started!");
			Socket socket = new Socket(args[1], Integer.parseInt(args[2]));
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                        String my_name = args[0];

                        MazePacket m = new MazePacket();
                        MazeSignature sig = new MazeSignature(my_name);
                        m.type = MazePacket.MAZE_CONNECT;
                        m.sig = sig;
                        out.writeObject(m);

                        MazePacket m2 = (MazePacket) in.readObject();
                        if (m2.type == MazePacket.MAZE_NULL) {
                                System.out.println(m2.err);
                                quit();
                        }

                        if (m2.type != MazePacket.MAZE_CONNECT || m2.sig.key().compareTo(sig.key()) != 0) {
				quit();
                        }

                        // Create the maze
                        maze = new MazeImpl(new Point(m2.mazeWidth, m2.mazeHeight), m2.mazeSeed);
                        assert(maze != null);
                        
                        // Have the ScoreTableModel listen to the maze to find
                        // out how to adjust scores.
                        ScoreTableModel scoreModel = new ScoreTableModel();
                        assert(scoreModel != null);
                        maze.addMazeListener(scoreModel);
                        
                        // Create the GUIClient and connect it to the KeyListener queue
                        guiClient = new GUIClient(my_name, maze, out);
                        maze.addClient(guiClient, m2.p);
                        this.addKeyListener(guiClient);

                        HashMap<String, Client> directory = new HashMap<String, Client>(); // mapping of key to client
                        directory.put(my_name, guiClient);

			if (m2.remote_clients != null) {
				for (int i=0; i<m2.remote_clients.size(); i++) {
					String name = m2.remote_clients.get(i);
					RemoteClient remoteClient = new RemoteClient(name);
					maze.addClient(remoteClient, m2.current_pos.get(name));
					scoreModel.setScore(remoteClient, m2.current_score.get(name));
					directory.put(name, remoteClient);
					consolePrintLn("Client \""+name+"\" has joined the game.");
				}
			}

                        // Create the panel that will display the maze.
                        overheadPanel = new OverheadMazePanel(maze, guiClient);
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

                        ClientThread t = new ClientThread(maze, sig, socket, out, in, scoreModel, directory, m2.seq_no + 1);
                        new Thread(t).start();

                        // Let the magic begin.
                        setVisible(true);
                        overheadPanel.repaint();
                        this.requestFocusInWindow();

                } catch (Exception e) {
                        System.out.println(e);
                }

        }
        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String[] args) {

                if (args.length != 3) {
                        System.out.println("ERORR: run as Mazwar <name> <server hostname> <server port>");
                        System.exit(-1);
                }
                /* Create the GUI */
                new Mazewar(args);
        }
}

class ClientThread implements Runnable {

        private Maze maze = null;
        private MazeSignature sig = null;
        private Socket socket = null;
        private ObjectOutputStream out = null;
        private ObjectInputStream in = null;
        private ScoreTableModel scoreModel = null;
        private HashMap<String, Client> directory = null;
        private int seq_no;
        private MazePacket mp = null;

        public ClientThread(Maze maze, MazeSignature sig, Socket socket, ObjectOutputStream out, ObjectInputStream in, ScoreTableModel scoreModel, HashMap<String, Client> directory, Integer seq_no) {
                this.maze = maze;
                this.sig = sig;
                this.socket = socket;
                this.out = out;
                this.in = in;
                this.seq_no = seq_no;
                this.scoreModel = scoreModel;
                this.directory = directory;
        }

        public void run() {
                try {
                        LinkedList<MazePacket> packetList = new LinkedList<MazePacket>();

                        while ((mp = (MazePacket) in.readObject()) != null) {
				// Disconnect from the game
				if (mp.seq_no == -1) { break; }

				// Find location to insert packet
                                int i = 0;
                                while (i < packetList.size() && (packetList.get(i).seq_no < mp.seq_no)) {
                                        i++;
                                }
                                packetList.add(i, mp);

				// Retrieve packet from the queue
                                if (packetList.peekFirst() != null && packetList.peekFirst().seq_no == seq_no) {
                                        seq_no++;
                                        MazePacket m = packetList.removeFirst();

                                        if (m.type == MazePacket.MAZE_DATA) {
						handle(m);
                                        } else if (m.type == MazePacket.MAZE_CONNECT) {
                                                String name = m.sig.key();
                                                RemoteClient remoteClient = new RemoteClient(name);
                                                maze.addClient(remoteClient, m.p);
                                                directory.put(name, remoteClient);
						Mazewar.consolePrintLn("Client \""+name+"\" has joined the game.");
                                        } else if (m.type == MazePacket.MAZE_DISCONNECT) {
                                                maze.removeClient(directory.remove(m.sig.key()));
						Mazewar.consolePrintLn("Client \""+m.sig.key()+"\" has left the game.");
                                        }
                                }
                        }
                        System.out.println("Done receiving.");
                        in.close();
                        out.close();
                        socket.close();

                } catch (Exception e) {
                        System.out.println("Error received");
                        System.out.println(e);
                }
		System.exit(0);
        }

        public boolean handle(MazePacket m) {
                if (m.ce == null) { return false; } // no action given
                Client c = directory.get(m.sig.key());
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
				maze.killClient(d, c, m.p);
				return true;
			}
                }
                return false;
        }
}
