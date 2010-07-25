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

import java.util.HashMap;
import java.util.Map;

import mplayeripc.MPlayerProcess.Error;

public class MPlayerMediaAnalyzer implements MPlayerProcessListener {
	
	private HashMap<String, String> specs = new HashMap<String, String>();
	
	private volatile boolean end = false;
	
	public MPlayerMediaAnalyzer(String mplayerLocation, String uri) throws MPlayerException {
		HashMap<String, String> args = new HashMap<String, String>();
		args.put("-vo", "null");
		args.put("-ao", "null");
		args.put("-msglevel", "identify=6");
		args.put("-frames", "1");
		args.put("-really-quiet", null);
		args.put("-noconfig", "all");
		args.put("-nocache", null);
		
		MPlayerProcess mpp = new MPlayerProcess(this, mplayerLocation, args, uri);
		mpp.createProcess();
		
		while (!end)
			try {Thread.sleep(1);} catch (Exception ignore) {}
	}

	@Override
	public void handleMplayerStdOutErr(String line) {
		String[] values = line.split("=");
		if (values.length == 2)
			specs.put(values[0], values[1]);
		else
			specs.put(line, "");
		//System.out.println(line);
	}
	
	public int getVideoWidth() {		
		return getIntegerValue("ID_VIDEO_WIDTH");
	}
	
	public int getVideoHeight() {
		return getIntegerValue("ID_VIDEO_HEIGHT");
	}
	
	private int getIntegerValue(String id) {
		String value = specs.get(id);
		return value==null?0:Integer.parseInt(value);
	}
	
	public Map<String, String> getMediaSpecs() {
		return specs;
	}

	@Override
	public void errorOccurred(Error error) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void processEnded(Integer exitValue) {
		end = true;
		// TODO Auto-generated method stub
		
	}

}
