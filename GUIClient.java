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

import java.io.*;
import java.util.*;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

/**
 * An implementation of {@link LocalClient} that is controlled by the keyboard
 * of the computer on which the game is being run.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: GUIClient.java 343 2004-01-24 03:43:45Z geoffw $
 */

public class GUIClient extends LocalClient implements KeyListener {

        private MazeSignature sig;
	private Maze maze;
        private ObjectOutputStream out = null;
        /**
         * Create a GUI controlled {@link LocalClient}.  
         */
        public GUIClient(String name) {
                super(name);
        }

        public GUIClient(String name, Maze maze, ObjectOutputStream out) {
                super(name);
                sig = new MazeSignature(name);
		this.maze = maze;
                this.out = out;
        }

        /**
         * Handle a key press.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyPressed(KeyEvent e) {
                MazePacket m = new MazePacket();
                m.sig = sig;
		// If the user pressed Q, invoke the cleanup code and quit. 
		if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
			m.type = MazePacket.MAZE_DISCONNECT;
			writeToServer(m);
		// Up-arrow moves forward.
		} else if(e.getKeyCode() == KeyEvent.VK_UP) {
			m.type = MazePacket.MAZE_DATA;
			m.ce = ClientEvent.moveForward;
			writeToServer(m);
		// Down-arrow moves backward.
		} else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
			m.type = MazePacket.MAZE_DATA;
			m.ce = ClientEvent.moveBackward;
			writeToServer(m);
		// Left-arrow turns left.
		} else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
			m.type = MazePacket.MAZE_DATA;
			m.ce = ClientEvent.turnLeft;
			writeToServer(m);
		// Right-arrow turns right.
		} else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
			m.type = MazePacket.MAZE_DATA;
			m.ce = ClientEvent.turnRight;
			writeToServer(m);
		// Spacebar fires.
		} else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
			if (!maze.getClientFired(this)) {
				m.type = MazePacket.MAZE_DATA;
				m.ce = ClientEvent.fire;
				writeToServer(m);
			}
		}
        }
        
        /**
         * Handle a key release. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyReleased(KeyEvent e) {
        }
        
        /**
         * Handle a key being typed. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyTyped(KeyEvent e) {
        }

	/**
	 * Write MazePacket to server.
	 */
	public synchronized void writeToServer (MazePacket m) {
		try {
			out.writeObject(m);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	/**
	 * Send death signal to server.
	 */
	public void reportDeath (String culprit) {
		MazePacket m = new MazePacket();
		m.sig = sig;
		m.type = MazePacket.MAZE_DATA;
		m.ce = ClientEvent.death;
		m.remote_clients = new ArrayList<String>();
		m.remote_clients.add(culprit);
		writeToServer(m);
	}

}
