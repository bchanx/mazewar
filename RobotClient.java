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

import java.util.*;
import java.util.Random;
import java.util.Vector;
import java.lang.Runnable;

/**
 * A very naive implementation of a computer controlled {@link LocalClient}.  Basically
 * it stumbles about and shoots.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: RobotClient.java 345 2004-01-24 03:56:27Z geoffw $
 */
 
public class RobotClient extends LocalClient implements Runnable {

	private MazeServer server = null;
	private MazeSignature sig = null;

        /**
         * Random number generator so that the robot can be
         * "non-deterministic".
         */ 
         private final Random randomGen = new Random();

         /**
          * The {@link Thread} object we use to run the robot control code.
          */
         private final Thread thread;
         
        /** 
         * Flag to say whether the control thread should be
         * running.
         */
        private boolean active = false;
   
        /**
         * Create a computer controlled {@link LocalClient}.
         * @param name The name of this {@link RobotClient}.
         */
        public RobotClient(MazeServer server) {
                super(server.get_sig().name);
                assert(server.get_sig().name != null);

		this.server = server;
		this.sig = new MazeSignature(server.get_sig());

                // Create our thread
                thread = new Thread(this);
        }
   
	public RobotClient(String name) {
		super(name);
		assert(name != null);

		thread = null;
	}

        /** 
         * Override the abstract {@link Client}'s registerMaze method so that we know when to start 
         * control thread.
         * @param maze The {@link Maze} that we are begin registered with.
         */
        public synchronized void registerMaze(Maze maze) {
                assert(maze != null);
                super.registerMaze(maze);

                // Get the control thread going.
		if (this.server != null) {
	                active = true;
        	        thread.start();
		}
        }
        
        /** 
         * Override the abstract {@link Client}'s unregisterMaze method so we know when to stop the 
         * control thread. 
         */
        public synchronized void unregisterMaze() {
		if (this.server != null) {
	                // Signal the control thread to stop
        	        active = false; 
                	// Wait half a second for the thread to complete.
	                try {
        	                thread.join(500);
                	} catch(Exception e) {
                        	// Shouldn't happen
	                }
		}
                super.unregisterMaze();
        }
    
        /** 
         * This method is the control loop for an active {@link RobotClient}. 
         */
        public void run() {
                // Put a spiffy message in the console
                Mazewar.consolePrintLn("Robot client \"" + this.getName() + "\" activated.");

                // Loop while we are active
                while(active) {
			MazePacket m = new MazePacket(sig);
			m.type = MazePacket.MAZE_REQ;

			int x = randomGen.nextInt(10);
			if (x < 2) {
                        	// Try to move forward
				m.ce = ClientEvent.moveForward;
				writeToServer(m);
			} else if (x < 3) {
				// turn left!
				m.ce = ClientEvent.turnLeft;
				writeToServer(m);
			} else if (x < 4) {
				// or perhaps turn right!
				m.ce = ClientEvent.turnRight;
				writeToServer(m);
			} else if (x < 7) {
 	                       // Shoot at things once and a while.
				if (!server.maze_getClientFired(this)) {
					m.ce = ClientEvent.fire;
					writeToServer(m);
				}
                        }
                        
                        // Sleep so the humans can possibly compete.
                        try {
                                thread.sleep(1000);
                        } catch(Exception e) {
                                // Shouldn't happen.
                        }
                }
        }

	/**
	* Write MazePacket to server and broadcast.
	*/
	public synchronized void writeToServer (MazePacket m) {
		m.seq_no = server.get_next_seq_no();
		server.queue_add(m);
		server.client_broadcast(m);
		m.type = MazePacket.MAZE_DATA;
		server.payload_add(m.key(), m);
	}

	/**
	* Send death signal to server.
	*/
	public void reportDeath (String robot, String culprit) {
		if (server != null) {
			MazePacket m = new MazePacket(sig);
			m.type = MazePacket.MAZE_REQ;
			m.ce = ClientEvent.death;
			m.remote_clients = new ArrayList<MazeSignature>();
			m.remote_clients.add(new MazeSignature(culprit));
			writeToServer(m);
		}
	}


}
