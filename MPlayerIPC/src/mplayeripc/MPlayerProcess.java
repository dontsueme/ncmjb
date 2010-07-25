/**
 * Author: Stefan Giermair ( zstegi@gmail.com )
 * 
 * This file is part of ncmjb.
 * ncmjb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ncmjb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with ncmjb.  If not, see <http://www.gnu.org/licenses/>.
 */

package mplayeripc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MPlayerProcess extends Thread {
	
	final private MPlayerProcessListener mpl;
	final private String mplayerLocation;
	final private Map<String, String> args;
	private String uri;
	private Process process;
	private StdOutErrThread stdouterrthread;
	private PrintStream stdin;
	private Integer exitValue = null;
	private boolean processAlive= false;
	private boolean processBuildFail = false;
	
	public MPlayerProcess (MPlayerProcessListener mpl, String mplayerLocation, Map<String, String> args, String uri) {
		this(mpl, mplayerLocation, args);
		this.uri = uri;
	}
	
	public MPlayerProcess (MPlayerProcessListener mpl, String mplayerLocation, Map<String, String> args) {
		if (mplayerLocation == null  || mpl == null)
			throw new NullPointerException("mpl and mplayerLocation are not allowed to be null");
		
		this.mpl = mpl;
		this.mplayerLocation = mplayerLocation;
		this.args = args;
		this.uri = null;
	}
	
	private List<String> generateProcessBuildList() {
		ArrayList<String> processbuildlist = 
			new ArrayList<String>((uri!=null?2:1)+(args!=null?args.size():0)*2);
		
		processbuildlist.add(mplayerLocation);
	
		if (args != null) {
			Set<String> keys = args.keySet();
			for (String key : keys) {
				if (key != null && !key.equals("")) {
					processbuildlist.add(key);
					if (args.get(key) != null && !args.get(key).equals(""))
						processbuildlist.add(args.get(key));
				}
			}		
		}
		
		if (uri != null)
			processbuildlist.add(uri);
		
		return processbuildlist;
	}
	
	public void createProcess() {
		start();
		
		// wait/block until ready
		while (!processAlive && exitValue == null && !processBuildFail) {
			try {Thread.sleep(1);} catch (Exception ignore) {}
		}
	}
 	
	public void run() {
		try {				
			ProcessBuilder pb = new ProcessBuilder(generateProcessBuildList());
			pb.redirectErrorStream(true); // because stderr doesn't work on my machine (merged with stdout anyway)
			process = pb.start();
			
			stdouterrthread = new StdOutErrThread(process.getInputStream());
			stdouterrthread.start();
			
			stdin = new PrintStream(process.getOutputStream());			
			
			processAlive = true;
			
			boolean interrupted;			
			do {
				try {
					interrupted = false;
					process.waitFor();
				} catch (InterruptedException ignore) {
					interrupted = true;
				}
			} while (interrupted);
			
			processAlive = false;
			exitValue = process.exitValue();
	
			//System.out.println("process ended: " + exitValue);
			
		} catch (IOException e) {
			//process could not build - maybe wrong path or no executionbit
			//e.printStackTrace();
			processBuildFail = true;
			Error bf = Error.buildFail;
			bf.setException(e);
			mpl.errorOccurred(bf);
		} 
		
		mpl.processEnded(exitValue);
	}
	
	public void forceStop() {
		stdouterrthread.kill();
		process.destroy();
	}
	
	public synchronized void sendCommand(String command) {
		try {
			stdin.print(command);
			stdin.print("\n");
			stdin.flush();
		} catch (Exception ignore) {}		
	}
	
	public void searchForLine(String str) {
		stdouterrthread.searchForLine(str);
	}
	
	public String getResult() {
		return stdouterrthread.getResult();
	}
	
	private class StdOutErrThread extends Thread {
		
		final private InputStream in;
		private volatile boolean run = true;
		
		private String cache = null;
		
		private Pattern pattern = null;
		private Matcher matcher = null;
		
		private long starttime;
		private boolean timeout;
		
		public StdOutErrThread(InputStream stdouterr) {
			in = stdouterr;
		}
		
		public void run() {
			InputStreamReader isr = new InputStreamReader(in);
			BufferedReader br = new BufferedReader(isr);
			
			String line;
			try {
				while (run && (line = br.readLine()) != null) {
					if (pattern != null){
						matcher = pattern.matcher(line);
						if (matcher.find()) {
							cache = line;
							pattern = null;						}
					}
					mpl.handleMplayerStdOutErr(line);
				}
			} catch (IOException e) {
				if (run) {
					//e.printStackTrace();
					Error bio = Error.brokenIO;
					bio.setException(e);
					mpl.errorOccurred(bio);
				}				
			}
		}	
		
		public synchronized void searchForLine(String str) {
			pattern = Pattern.compile(str);
		}
		
		public synchronized String getResult(){
			starttime = System.currentTimeMillis();
			timeout = true;
			do {
				try {
					Thread.sleep(1);
					if (System.currentTimeMillis() - starttime >= 5000) // one second timeout
						timeout = false;
				} catch (InterruptedException e) {}
			} while (pattern != null && timeout);
			
			String tcache = cache;
			cache = null;
			if (!timeout){
				pattern = null;
				tcache = "Failed to get property: timeout reached!";
			}			
			return tcache;
		}
		
		public void kill() {
			run = false;
		}
	}
	
	public enum Error {
		buildFail,
		brokenIO, 
		invalidArgument;
		
		private String err = null;
		private Exception exc = null;
		
		public void setErrorString(String err) {
			this.err = err;
		}
		
		public String getErrorString() {
			return err;
		}
		
		public void setException(Exception e) {
			exc = e;
		}
		
		public Exception getException() {
			return exc;
		}
		
		
	}
	
	/*@Override
	public void finalize() {
		forceStop();
	}*/
}
