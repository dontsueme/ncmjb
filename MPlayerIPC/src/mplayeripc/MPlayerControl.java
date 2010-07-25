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

import java.io.File;
import java.util.HashMap;

public class MPlayerControl implements MPlayerProcessListener {	
	
	//TODO: write junit-tests and comment it
	//TODO: complete it: step_property and some meta*
	
	public enum Pausing {
		non(""), on("pausing "), keep("pausing_keep "), 
		toogle("pausing_toggle "), keep_force("pausing_keep_force ");
		final private String pausing;
		Pausing(String pausing){
			this.pausing = pausing;
		}
		public String toString() {
			return pausing;
		}
	}
	
	enum DVDNav {
		up, down, left, right, menu, select, prev, mouse
	}
	
	enum OSDMenu {
		up, down, ok, cancel, hide
	}
	
	final private MPlayerProcess mpp;
	final private MPlayerProcessListener cclient;
		
	public MPlayerControl(String mplayerLocation, MPlayerProcessListener mpl) {
		this(mplayerLocation, mpl, null, null);
	}
	
	public MPlayerControl(String mplayerLocation, MPlayerProcessListener mpl, MPlayerSharedMemory msm) {
		this(mplayerLocation, mpl, msm, null);
	}
	
	public MPlayerControl(String mplayerLocation, MPlayerProcessListener mpl, 
			MPlayerSharedMemory msm, HashMap<String, String> args) {		
		
		cclient = mpl;
		if (args == null)
			args = new HashMap<String, String>();		
		
		if (msm != null) {
			args.put("-vo", "tga:mode="+msm.getBufferCount()+":id="+msm.getID());			
			args.put("-vf", "expand=osd=1");
		}
		
		if (!args.containsKey("-ao")) args.put("-ao", "pulse");
		
		if (!args.containsKey("-subfont-autoscale")) args.put("-subfont-autoscale", "1");
		if (!args.containsKey("-noass")) args.put("-ass", null);
		if (!args.containsKey("-nofontconfig")) args.put("-fontconfig", null);
		if (!args.containsKey("-noembeddedfonts")) args.put("-embeddedfonts", null);
		
		// to reset noconfig-param, set it to null
		if (!args.containsKey("-noconfig")) args.put("-noconfig", "all");
		if (args.get("-noconfig") == null) args.remove("-noconfig"); 
		
		if (!args.containsKey("-cache")) args.put("-cache", "10240");
		
		String lavdopts = args.get("-lavdopts"); 		
		if (lavdopts == null)
			lavdopts = "";
		if (!lavdopts.contains("threads"))
				args.put("-lavdopts", lavdopts + (lavdopts.equals("")?"":":") +"threads="+Runtime.getRuntime().availableProcessors());
		
		args.put("-quiet", null);
		args.put("-slave", null);
		args.put("-idle", null);
		
		mpp = new MPlayerProcess(this, mplayerLocation, args);
		mpp.createProcess();
	}
	
	//TODO: step_property_osd-switch and implement all step_properties	
	private synchronized void stepMplayerProperty(String property, String value, int direction , Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "step_property " + property + " " + value + " " + direction);
	}	
	
	//TODO: set_property_osd-switch
	private synchronized void setMplayerProperty(String property, String value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "set_property " + property + " " + value);
	}	
	
	//TODO: add timeout in MplayerProcess
	private synchronized String getValue(String command, String regexp) {
		mpp.searchForLine(regexp);
		mpp.sendCommand(command);
		return mpp.getResult();
	}	
	
	private String getGenericMplayerProperty(String property, String regexp, Pausing pausing) {
		return getValue(pausing.toString() + "get_property " + property, regexp);
	}
		
	private Float getFloatMplayerProperty(String property, String regexp, int substringbeginindex, Pausing pausing) {
		try { 
			return Float.parseFloat(getGenericMplayerProperty(property, regexp, pausing).substring(substringbeginindex)); 
		} catch (Exception e) { 
			//e.printStackTrace(); 
		}
		return null;	
	}
	
	private String getStringMplayerProperty(String property, String regexp, int substringbeginindex, Pausing pausing) {
		try {
			String temp = getGenericMplayerProperty(property, regexp, pausing);
			if (temp.startsWith("Fail")){
				//System.err.println(temp);
				return null;
			}
			return temp.substring(substringbeginindex);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}	
	
	public void handleMplayerStdOutErr(String line) {
		if (cclient != null)
			cclient.handleMplayerStdOutErr(line);
		//System.out.println(line);
	}	
	
	public void errorOccurred(MPlayerProcess.Error error) {
		if (cclient != null)
			cclient.errorOccurred(error);
		else {
			if (error.getErrorString() != null)
				System.out.println("" + error + error.getErrorString());
			else {
				System.out.println(error);
				error.getException().printStackTrace();
			}
		}
	}
	
	public void processEnded(Integer exitValue) {
		if (cclient != null)
			cclient.processEnded(exitValue);
		//System.out.println("Control exitValue: "+ exitValue);
	}	
	
	public void setAudioDelay(float sec, int abs, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + sec + " " + abs);
	}
	
	public void setAudioDelay(float sec, int abs) {
		setAudioDelay(sec, abs, Pausing.non);
	}
	
	public void setAudioDelay(float sec) {
		setAudioDelay(sec, 0, Pausing.non);
	}
	
	public void changeRectangle(int val1, int val2, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "change_rectangle " + val1 + " " + val2);
	}
	
	public void changeRectangle(int val1, int val2) {
		changeRectangle(val1, val2, Pausing.non);
	}
	
	public void setDVBChannel(int channel, int cardnumber, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "dvb_set_channel " + channel + " " + cardnumber);
	}
	
	public void setDVBChannel(int channel, int cardnumber) {
		setDVBChannel(channel, cardnumber, Pausing.non);
	}
	
	public void pressDVDNavButton(DVDNav button_name, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "dvdnav" + " " + button_name);
	}
	
	public void pressDVDNavButton(DVDNav button_name) {
		pressDVDNavButton(button_name, Pausing.non);
	}
	
	public void setEdlMark(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "edl_mark");
	}
	
	public void setEdlMark() {
		setEdlMark(Pausing.non);
	}
	
	public void screenshot(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "screenshot " + value);
	}
	
	public void screenshot(int value) {
		screenshot(value, Pausing.non);
	}
	
	public void sendKeyDownEvent(int keycode) {
		mpp.sendCommand("key_down_event " + keycode);
	}
	
	public void loadFile(String uri, int append, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "loadfile " + uri + " " + append);
		//TODO: blocking if possible
	}
	
	public void loadList(String file, int append, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "loadlist " + file + " " + append);
		//TODO: blocking if possible
	}
	
	public void play(String uri, Pausing pausing) {
		String ret;
		if (!(new File(uri)).isDirectory()) {
			ret = getValue(pausing.toString() + "loadfile " + "\""+uri+"\"", "Starting playback|Failed");			
		} else {
			ret = "Failed, is a directory: " + uri;
		}
		
		if (ret.contains("Failed")) {
			MPlayerProcess.Error err = MPlayerProcess.Error.invalidArgument; 
			if (ret.contains("timeout"))
				err.setErrorString("Failed to open (timeout) " + uri);
			else 
				err.setErrorString(ret);
			cclient.errorOccurred(err);
		}
	}
	
	public void play(String uri) {
		play(uri, Pausing.non);
	}
	
	public void setLoop(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "loop " + value);
	}
	
	public void setLoop(int value) {
		setLoop(value, Pausing.non);
	}
	
	public void sendMenuCmd(OSDMenu cmd, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "menu " + cmd);
	}
	
	public void sendMenuCmd(OSDMenu cmd) {
		sendMenuCmd(cmd, Pausing.non);
	}
	
	public void setMenu(String menu_name, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "set_menu " + menu_name);
	}
	
	public void setMenu(String menu_name) {
		setMenu(menu_name, Pausing.non);
	}
	
	public void showOSDPropertyText(String text, int duration, int level, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "osd_show_property_text " + text + " " + duration + " " + level);
	}
	
	public void showOSDPropertyText(String text, int duration, int level) {
		showOSDPropertyText(text, duration, level, Pausing.non);
	}
	
	public void showOSDPropertyText(String text, int duration) {
		showOSDPropertyText(text, duration, 0);
	}
	
	public void showOSDText(String text, int duration, int level, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "osd_show_text " + text + " " + duration + " " + level);
	}
	
	public void showOSDText(String text, int duration, int level) {
		showOSDText(text, duration, level, Pausing.non);
	}
	
	public void showOSDText(String text, int duration) {
		showOSDText(text, duration, 0);
	}
	
	public void setPanScan(int value, int abs, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "panscan " + value + " " + abs);
	}
	
	public void setPanScan(int value, int abs) {
		setPanScan(value, abs, Pausing.non);
	}
	
	public void pause() {
		mpp.sendCommand("pause");
	}
	
	public void frameStep(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "frame_step");
	}
	
	public void frameStep() {
		frameStep(Pausing.non);
	}
	
	public void stepInPlayTree(int value, int force, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "pt_step " + value + " " + force);
	}
	
	public void stepInPlayTree(int value) {
		stepInPlayTree(value, 0, Pausing.non);
	}
	
	public void stepUpInPlayTree(int value, int force, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "pt_up_step " + value + " " + force);
	}
	
	public void stepUpInPlayTree(int value) {
		stepUpInPlayTree(value, 0, Pausing.non);
	}	
	
	public void quit(int returnvalue) {
		mpp.sendCommand("quit " + returnvalue);
	}
	
	public void quit() {
		mpp.sendCommand("quit");
		try { // TODO: wait that processEnded is triggered
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
	}
	
	public void setRadioChannel(int channel, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "radio_set_channel " + channel);
	}
	
	public void setRadioChannel(int channel) {
		setRadioChannel(channel, Pausing.non);
	}
	
	public void setRadioFrequency(float mhz, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "radio_set_freq " + mhz);
	}
	
	public void setRadioFrequency(float mhz) {
		setRadioFrequency(mhz, Pausing.non);
	}
	
	public void stepRadioChannel(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "radio_step_channel " + value);
	}
	
	public void stepRadioChannel(int value) {
		stepRadioChannel(value, Pausing.non);
	}
	
	public void stepRadioFrequency(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "radio_step_freq " + value);
	}
	
	public void stepRadioFrequency(int value) {
		stepRadioFrequency(value, Pausing.non);
	}
	
	public void seek(float sec, int type, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "seek " + sec + " " + type);
	}
	
	public void seek(float sec, int type) {
		seek(sec, type, Pausing.keep_force);
	}
	
	public void seek(float sec) {
		seek(sec, 0, Pausing.keep_force);
	}
	
	public void seekChapter(int value, int type, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + value + " " + type);
	}
	
	public void seekChapter(int value, int type) {
		seekChapter(value, type, Pausing.non);
	}
	
	public void setMousePos(int x, int y) {
		mpp.sendCommand("set_mouse_pos " + x + " " + y);
	}
	
	public void speedIncr(float value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "speed_incr " + value);
	}
	
	public void speedIncr(float value) {
		speedIncr(value, Pausing.non);
	}
	
	public void speedMult(float value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "speed_mult " + value);
	}
	
	public void speedMult(float value) {
		speedMult(value, Pausing.non);
	}	
	
	public void stop() {
		mpp.sendCommand("stop");
	}
	
	public void loadSub(String file, Pausing pausing) {
		//TODO
	}
	
	public void loadSub(String file) {
		loadSub(file, Pausing.non);
	}
	
	public void logSub(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "sub_log");
	}
	
	public void logSub() {
		logSub(Pausing.non);
	}
	
	public void removeSub(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "sub_remove " + value);
	}
	
	public void removeSub(int value) {
		removeSub(value, Pausing.non);
	}
	
	public void setVideoAspectRatio(float aspect, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "switch_ratio " + aspect);
	}
	
	public void setVideoAspectRatio(float aspect) {
		setVideoAspectRatio(aspect, Pausing.non);
	}
	
	public void addTeletextDigit(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "teletext_add_digit " + value);
	}
	
	public void addTeletextDigit(int value) {
		addTeletextDigit(value, Pausing.non);
	}
	
	public void goTeletextLink(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "teletext_go_link " + value);
	}
	
	public void goTeletextLink(int value) {
		goTeletextLink(value, Pausing.non);
	}
	
	public void startTVScan(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_start_scan");
	}
	
	public void startTVScan() {
		startTVScan(Pausing.non);
	}
	
	public void stepTVChannel(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_step_channel " + value);
	}
	
	public void stepTVChannel(int value) {
		stepTVChannel(value, Pausing.non);
	}
	
	public void stepTVNorm(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_step_norm");
	}
	
	public void stepTVNorm() {
		stepTVNorm(Pausing.non);
	}
	
	public void stepTVChanlist(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_step_chanlist");
	}
	
	public void stepTVChanlist() {
		stepTVChanlist(Pausing.non);
	}
	
	public void setTVChannel(int value, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_set_channel " + value);
	}
	
	public void setTVChannel(int value) {
		setTVChannel(value, Pausing.non);
	}
	
	public void stepTVLastChannel(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_last_channel");
	}
	
	public void stepTVLastChannel() {
		stepTVLastChannel(Pausing.non);
	}
	
	public void setTVFrequency(float mhz, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_set_freq " + mhz);
	}
	
	public void setTVFrequency(float mhz) {
		setTVFrequency(mhz, Pausing.non);
	}
	
	public void stepTVFrequency(float mhz, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_step_freq " + mhz);
	}
	
	public void stepTVFrequency(float mhz) {
		stepTVFrequency(mhz, Pausing.non);
	}
	
	public void setTVNorm(String norm, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "tv_set_norm " + norm);
	}
	
	public void setTVNorm(String norm) {
		setTVNorm(norm, Pausing.non);
	}
	
	public void useMaster(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "use_master");
	}
	
	public void useMaster() {
		useMaster(Pausing.non);
	}
	
	public void exit(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "exit");
	}
	
	public void exit() {
		exit(Pausing.non);
	}
	
	public void hide(Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "hide");
	}
	
	public void hide() {
		hide(Pausing.non);
	}
	
	public void run(String cmd, Pausing pausing) {
		mpp.sendCommand(pausing.toString() + "run " + cmd);
	}
	
	public void run(String cmd) {
		run(cmd, Pausing.non);
	}
	
	public void setOSDLevel(int level, Pausing pausing) {
		setMplayerProperty("osdlevel", ""+level, pausing);
	}
	
	public void setOSDLevel(int level) {
		setOSDLevel(level, Pausing.non);
	}
	
	public Float getOSDLevel(Pausing pausing) {
		return this.getFloatMplayerProperty("osdlevel", "osdlevel", "ANS_osd_level=".length(), pausing);
	}
	
	public Float getOSDLevel() {
		return getOSDLevel(Pausing.non);
	}
	
	//public void stepOSDLevel(int direction)
	
	public void setSpeed(float speed, Pausing pausing) {
		setMplayerProperty("speed", ""+speed, pausing);
	}
	
	public void setSpeed(float speed) {
		setSpeed(speed, Pausing.non);
	}
	
	public Float getSpeed(Pausing pausing) {
		return this.getFloatMplayerProperty("speed", "speed", "ANS_speed=".length(), pausing);
	}
	
	public Float getSpeed() {
		return getSpeed(Pausing.non);
	}
	
	public void setLoop(float loop, Pausing pausing) {
		setMplayerProperty("loop", ""+loop, pausing);
	}
	
	public void setLoop(float loop) {
		setLoop(loop, Pausing.non);
	}
	
	public Float getLoop(Pausing pausing) {
		return this.getFloatMplayerProperty("loop", "loop", "ANS_loop=".length(), pausing);
	}
	
	public Float getLoop() {
		return getLoop(Pausing.non);
	}
	
	public Boolean isPaused(Pausing pausing) {
		String temp = getStringMplayerProperty("pause", "pause", "ANS_pause=".length(), pausing);
		if (temp != null) {
			if (temp.equals("yes"))
				return true;
			else if (temp.equals("no"))
				return false;
		}
		return null;
	}
	
	public Boolean isPaused() {
		return isPaused(Pausing.keep_force);
	}
	
	public String getFileName(Pausing pausing) {
		return getStringMplayerProperty("filename", "filename", "ANS_filename=".length(), pausing);
	}
	
	public String getFileName() {
		return getFileName(Pausing.non);
	}
	
	public String getPath(Pausing pausing) {
		return getStringMplayerProperty("path", "path", "ANS_path=".length(), pausing);
	}
	
	public String getPath() {
		return getPath(Pausing.non);
	}
	
	public String getDemuxer(Pausing pausing) {
		return getStringMplayerProperty("demuxer", "demuxer", "ANS_demuxer=".length(), pausing);
	}
	
	public String getDemuxer() {
		return getDemuxer(Pausing.non);
	}
	
	public void setStreamPos(float stream_pos, Pausing pausing) {
		setMplayerProperty("stream_pos", ""+stream_pos, pausing);
	}
	
	public void setStreamPos(float stream_pos) {
		setStreamPos(stream_pos, Pausing.non);
	}
	
	public Float getStreamPos(Pausing pausing) {
		return this.getFloatMplayerProperty("stream_pos", "stream_pos", "ANS_stream_pos=".length(), pausing);
	}
	
	public Float getStreamPos() {
		return getStreamPos(Pausing.non);
	}
	
	public Float getStreamStart(Pausing pausing) {
		return this.getFloatMplayerProperty("stream_start", "stream_start", "ANS_stream_start=".length(), pausing);
	}
	
	public Float getStreamStart() {
		return getStreamStart(Pausing.non);
	}
	
	public Float getStreamEnd(Pausing pausing) {
		return getFloatMplayerProperty("stream_end", "stream_end", "ANS_stream_end=".length(), pausing);
	}
	
	public Float getStreamEnd() {
		return getStreamEnd(Pausing.non);
	}
	
	public Float getStreamLength(Pausing pausing) {
		return getFloatMplayerProperty("stream_length", "stream_length", "ANS_stream_length=".length(),pausing);
	}
	
	public Float getStreamLength() {
		return getStreamLength(Pausing.non);
	}
	
	public void setChapter(int chapter, Pausing pausing) {
		setMplayerProperty("chapter", ""+chapter, pausing);
	}
	
	public void setChapter(int chapter) {
		setChapter(chapter, Pausing.non);
	}
	
	public Float getChapter(Pausing pausing) {
		return this.getFloatMplayerProperty("chapter", "chapter", "ANS_chapter=".length(), pausing);
	}
	
	public Float getChapter() {
		return getChapter(Pausing.non);
	}
	
	public Float getChapters(Pausing pausing) {
		return getFloatMplayerProperty("chapters", "chapters", "ANS_chapters=".length(), pausing);
	}
	
	public Float getChapters() {
		return getChapters(Pausing.non);
	}
	
	public void setAngle(int angle, Pausing pausing) {
		setMplayerProperty("angle", ""+angle, pausing);
	}
	
	public void setAngle(int angle) {
		setAngle(angle, Pausing.non);
	}
	
	public Float getAngle(Pausing pausing) {
		return this.getFloatMplayerProperty("angle", "angle", "ANS_angle=".length(), pausing);
	}
	
	public Float getAngle() {
		return getAngle(Pausing.non);
	}
	
	public Float getLength(Pausing pausing) {
		return getFloatMplayerProperty("length", "length", "ANS_length=".length(), pausing);
	}
	
	public Float getLength() {
		return getLength(Pausing.non);
	}
	
	public void setPercentPos(int percent_pos, Pausing pausing) {
		setMplayerProperty("percent_pos", ""+percent_pos, pausing);
	}
	
	public void setPercentPos(int percent_pos) {
		setPercentPos(percent_pos, Pausing.non);
	}
	
	public Float getPercentPos(Pausing pausing) {
		return this.getFloatMplayerProperty("percent_pos", "percent_pos", "ANS_percent_pos=".length(), pausing);
	}
	
	public Float getPercentPos() {
		return getPercentPos(Pausing.non);
	}
	
	public void setTimePos(float time_pos, Pausing pausing) {
		setMplayerProperty("time_pos", ""+time_pos, pausing);
	}
	
	public void setTimePos(float time_pos) {
		setTimePos(time_pos, Pausing.non);
	}
	
	public Float getTimePos(Pausing pausing) {
		return this.getFloatMplayerProperty("time_pos", "time_pos", "ANS_time_pos=".length(), pausing);
	}
	
	public Float getTimePos() {
		return getTimePos(Pausing.non);
	}
	
	public String getMetadata(Pausing pausing) {
		return getStringMplayerProperty("metadata", "metadata", "ANS_metadata=".length(), pausing);
	}
	
	public String getMetadata() {
		return getMetadata(Pausing.non);
	}
	
	public void setVolume(float volume, Pausing pausing) {
		setMplayerProperty("volume", ""+volume, pausing);
	}
	
	public void setVolume(float volume) {
		setVolume(volume, Pausing.non);
	}
	
	public Float getVolume(Pausing pausing) {
		return this.getFloatMplayerProperty("volume", "volume", "ANS_volume=".length(), pausing);
	}
	
	public Float getVolume() {
		return getVolume(Pausing.non);
	}
	
	public void setBalance(float balance, Pausing pausing) {
		setMplayerProperty("balance", ""+balance, pausing);
	}
	
	public void setBalance(float balance) {
		setBalance(balance, Pausing.non);
	}
	
	public Float getBalance(Pausing pausing) {
		return this.getFloatMplayerProperty("balance", "balance", "ANS_balance=".length(), pausing);
	}
	
	public Float getBalance() {
		return getBalance(Pausing.non);
	}
	
	public void setMute(float mute, Pausing pausing) {
		setMplayerProperty("mute", ""+mute, pausing);
	}
	
	public void setMute(float mute) {
		setMute(mute, Pausing.non);
	}
	
	public Float getMute(Pausing pausing) {
		return this.getFloatMplayerProperty("mute", "mute", "ANS_mute=".length(), pausing);
	}
	
	public Float getMute() {
		return getMute(Pausing.non);
	}
	
	public void setAudioDelayProperty(float audio_delay, Pausing pausing) {
		setMplayerProperty("audio_delay", ""+audio_delay, pausing);
	}
	
	public void setAudioDelayProperty(float audio_delay) {
		setAudioDelayProperty(audio_delay, Pausing.non);
	}
	
	public Float getAudioDelay(Pausing pausing) {
		return this.getFloatMplayerProperty("audio_delay", "audio_delay", "ANS_audio_delay=".length(), pausing);
	}
	
	public Float getAudioDelay() {
		return getAudioDelay(Pausing.non);
	}
	
	public Float getAudioFormat(Pausing pausing) {
		return getFloatMplayerProperty("audio_format", "audio_format", "ANS_audio_format=".length() , pausing);
	}
	
	public Float getAudioFormat() {
		return getAudioFormat(Pausing.non);
	}
	
	public String getAudioCodec(Pausing pausing) {
		return getStringMplayerProperty("audio_codec", "audio_codec", "ANS_audio_codec=".length(), pausing);
	}
	
	public String getAudioCodec() {
		return getAudioCodec(Pausing.non);
	}
	
	public Float getAudioBitrate(Pausing pausing) {
		return getFloatMplayerProperty("audio_bitrate", "audio_bitrate", "ANS_audio_bitrate=".length() , pausing);
	}
	
	public Float getAudioBitrate() {
		return getAudioBitrate(Pausing.non);
	}
	
	public Float getSamplerate(Pausing pausing) {
		return getFloatMplayerProperty("samplerate", "samplerate", "ANS_samplerate=".length() , pausing);
	}
	
	public Float getSamplerate() {
		return getSamplerate(Pausing.non);
	}
	
	public Float getChannels(Pausing pausing) {
		return getFloatMplayerProperty("channels", "channels", "ANS_channels=".length() , pausing);
	}
	
	public Float getChannels() {
		return getChannels(Pausing.non);
	}
	
	public void setSwitchAudio(int switch_audio, Pausing pausing) {
		setMplayerProperty("switch_audio", ""+switch_audio, pausing);
	}
	
	public void setSwitchAudio(int switch_audio) {
		setSwitchAudio(switch_audio, Pausing.non);
	}
	
	public Float getSwitchAudio(Pausing pausing) {
		return this.getFloatMplayerProperty("switch_audio", "switch_audio", "ANS_switch_audio=".length(), pausing);
	}
	
	public Float getSwitchAudio() {
		return getSwitchAudio(Pausing.non);
	}
	
	public void setSwitchAngle(int switch_angle, Pausing pausing) {
		setMplayerProperty("switch_angle", ""+switch_angle, pausing);
	}
	
	public void setSwitchAngle(int switch_angle) {
		setSwitchAngle(switch_angle, Pausing.non);
	}
	
	public Float getSwitchAngle(Pausing pausing) {
		return this.getFloatMplayerProperty("switch_angle", "switch_angle", "ANS_switch_angle=".length(), pausing);
	}
	
	public Float getSwitchAngle() {
		return getSwitchAngle(Pausing.non);
	}
	
	public void setSwitchTitle(int switch_title, Pausing pausing) {
		setMplayerProperty("switch_title", ""+switch_title, pausing);
	}
	
	public void setSwitchTitle(int switch_title) {
		setSwitchTitle(switch_title, Pausing.non);
	}
	
	public Float getSwitchTitle(Pausing pausing) {
		return this.getFloatMplayerProperty("switch_title", "switch_title", "ANS_switch_title=".length(), pausing);
	}
	
	public Float getSwitchTitle() {
		return getSwitchTitle(Pausing.non);
	}
	
	public void setFullscreen(int fullscreen, Pausing pausing) {
		setMplayerProperty("fullscreen", ""+fullscreen, pausing);
	}
	
	public void setFullscreen(int fullscreen) {
		setFullscreen(fullscreen, Pausing.non);
	}
	
	public Float getFullscreen(Pausing pausing) {
		return this.getFloatMplayerProperty("fullscreen", "fullscreen", "ANS_fullscreen=".length(), pausing);
	}
	
	public Float getFullscreen() {
		return getFullscreen(Pausing.non);
	}
	
	public void setDeinterlace(int deinterlace, Pausing pausing) {
		setMplayerProperty("deinterlace", ""+deinterlace, pausing);
	}
	
	public void setDeinterlace(int deinterlace) {
		setDeinterlace(deinterlace, Pausing.non);
	}
	
	public Float getDeinterlace(Pausing pausing) {
		return this.getFloatMplayerProperty("deinterlace", "deinterlace", "ANS_deinterlace=".length(), pausing);
	}
	
	public Float getDeinterlace() {
		return getDeinterlace(Pausing.non);
	}
	
	public void setOnTop(int ontop, Pausing pausing) {
		setMplayerProperty("ontop", ""+ontop, pausing);
	}
	
	public void setOnTop(int ontop) {
		setOnTop(ontop, Pausing.non);
	}
	
	public Float getOnTop(Pausing pausing) {
		return this.getFloatMplayerProperty("ontop", "ontop", "ANS_ontop=".length(), pausing);
	}
	
	public Float getOnTop() {
		return getOnTop(Pausing.non);
	}
	
	public void setRootwin(int rootwin, Pausing pausing) {
		setMplayerProperty("rootwin", ""+rootwin, pausing);
	}
	
	public void setRootwin(int rootwin) {
		setRootwin(rootwin, Pausing.non);
	}
	
	public Float getRootwin(Pausing pausing) {
		return this.getFloatMplayerProperty("rootwin", "rootwin", "ANS_rootwin=".length(), pausing);
	}
	
	public Float getRootwin() {
		return getRootwin(Pausing.non);
	}
	
	public void setBorder(int border, Pausing pausing) {
		setMplayerProperty("border", ""+border, pausing);
	}
	
	public void setBorder(int border) {
		setBorder(border, Pausing.non);
	}
	
	public Float getBorder(Pausing pausing) {
		return this.getFloatMplayerProperty("border", "border", "ANS_border=".length(), pausing);
	}
	
	public Float getBorder() {
		return getBorder(Pausing.non);
	}
	
	public void setFrameDropping(int framedropping, Pausing pausing) {
		setMplayerProperty("framedropping", ""+framedropping, pausing);
	}
	
	public void setFrameDropping(int framedropping) {
		setFrameDropping(framedropping, Pausing.non);
	}
	
	public Float getFrameDropping(Pausing pausing) {
		return this.getFloatMplayerProperty("framedropping", "framedropping", "ANS_framedropping=".length(), pausing);
	}
	
	public Float getFrameDropping() {
		return getFrameDropping(Pausing.non);
	}
	
	public void setGamma(int gamma, Pausing pausing) {
		setMplayerProperty("gamma", ""+gamma, pausing);
	}
	
	public void setGamma(int gamma) {
		setGamma(gamma, Pausing.non);
	}
	
	public Float getGamma(Pausing pausing) {
		return this.getFloatMplayerProperty("gamma", "gamma", "ANS_gamma=".length(), pausing);
	}
	
	public Float getGamma() {
		return getGamma(Pausing.non);
	}
	
	public void setBrightness(int brightness, Pausing pausing) {
		setMplayerProperty("brightness", ""+brightness, pausing);
	}
	
	public void setBrightness(int brightness) {
		setBrightness(brightness, Pausing.non);
	}
	
	public Float getBrightness(Pausing pausing) {
		return this.getFloatMplayerProperty("brightness", "brightness", "ANS_brightness=".length(), pausing);
	}
	
	public Float getBrightness() {
		return getBrightness(Pausing.non);
	}
	
	public void setContrast(int contrast, Pausing pausing) {
		setMplayerProperty("contrast", ""+contrast, pausing);
	}
	
	public void setContrast(int contrast) {
		setContrast(contrast, Pausing.non);
	}
	
	public Float getContrast(Pausing pausing) {
		return this.getFloatMplayerProperty("contrast", "contrast", "ANS_contrast=".length(), pausing);
	}
	
	public Float getContrast() {
		return getContrast(Pausing.non);
	}
	
	public void setSaturation(int saturation, Pausing pausing) {
		setMplayerProperty("saturation", ""+saturation, pausing);
	}
	
	public void setSaturation(int saturation) {
		setSaturation(saturation, Pausing.non);
	}
	
	public Float getSaturation(Pausing pausing) {
		return this.getFloatMplayerProperty("saturation", "saturation", "ANS_saturation=".length(), pausing);
	}
	
	public Float getSaturation() {
		return getSaturation(Pausing.non);
	}
	
	public void setHue(int hue, Pausing pausing) {
		setMplayerProperty("hue", ""+hue, pausing);
	}
	
	public void setHue(int hue) {
		setHue(hue, Pausing.non);
	}
	
	public Float getHue(Pausing pausing) {
		return this.getFloatMplayerProperty("hue", "hue", "ANS_hue=".length(), pausing);
	}
	
	public Float getHue() {
		return getHue(Pausing.non);
	}
	
	public void setPanscan(float panscan, Pausing pausing) {
		setMplayerProperty("panscan", ""+panscan, pausing);
	}
	
	public void setPanscan(float panscan) {
		setPanscan(panscan, Pausing.non);
	}
	
	public Float getPanscan(Pausing pausing) {
		return this.getFloatMplayerProperty("panscan", "panscan", "ANS_panscan=".length(), pausing);
	}
	
	public Float getPanscan() {
		return getPanscan(Pausing.non);
	}
	
	public void setVSync(int vsync, Pausing pausing) {
		setMplayerProperty("vsync", ""+vsync, pausing);
	}
	
	public void setVSync(int vsync) {
		setVSync(vsync, Pausing.non);
	}
	
	public Float getVSync(Pausing pausing) {
		return this.getFloatMplayerProperty("vsync", "vsync", "ANS_vsync=".length(), pausing);
	}
	
	public Float getVSync() {
		return getVSync(Pausing.non);
	}
	
	public Float getVideoFormat(Pausing pausing) {
		return getFloatMplayerProperty("video_format", "video_format", "ANS_video_format=".length() , pausing);
	}
	
	public Float getVideoFormat() {
		return getVideoFormat(Pausing.non);
	}
	
	public String getVideoCodec(Pausing pausing) {
		return getStringMplayerProperty("video_codec", "video_codec", "ANS_video_codec".length(), pausing);
	}
	
	public String getVideoCodec() {
		return getVideoCodec(Pausing.non);
	}
	
	public Float getVideoBitrate(Pausing pausing) {
		return getFloatMplayerProperty("video_bitrate", "video_bitrate", "ANS_video_bitrate=".length() , pausing);
	}
	
	public Float getVideoBitrate() {
		return getVideoBitrate(Pausing.non);
	}
	
	public Float getDisplayWidth(Pausing pausing) {
		return getFloatMplayerProperty("width", "width", "ANS_width=".length() , pausing);
	}
	
	public Float getDisplayWidth() {
		return getDisplayWidth(Pausing.non);
	}
	
	public Float getDisplayHeight(Pausing pausing) {
		return getFloatMplayerProperty("height", "height", "ANS_height=".length() , pausing);
	}
	
	public Float getDisplayHeight() {
		return getDisplayHeight(Pausing.non);
	}
	
	public Float getFPS(Pausing pausing) {
		return getFloatMplayerProperty("fps", "fps", "ANS_fps=".length() , pausing);
	}
	
	public Float getFPS() {
		return getFPS(Pausing.non);
	}
	
	public Float getVideoAspect(Pausing pausing) {
		return getFloatMplayerProperty("aspect", "ANS_aspect|'aspect'", "ANS_aspect=".length(), pausing);
	}
	
	public Float getVideoAspect() {
		return getVideoAspect(Pausing.non);
	}
	
	public void setSwitchVideo(int switch_video, Pausing pausing) {
		setMplayerProperty("switch_video", ""+switch_video, pausing);
	}
	
	public void setSwitchVideo(int switch_video) {
		setSwitchVideo(switch_video, Pausing.non);
	}
	
	public Float getSwitchVideo(Pausing pausing) {
		return this.getFloatMplayerProperty("switch_video", "switch_video", "ANS_switch_video=".length(), pausing);
	}
	
	public Float getSwitchVideo() {
		return getSwitchVideo(Pausing.non);
	}
	
	public void setSwitchProgram(int switch_program, Pausing pausing) {
		setMplayerProperty("switch_program", ""+switch_program, pausing);
	}
	
	public void setSwitchProgram(int switch_program) {
		setSwitchProgram(switch_program, Pausing.non);
	}
	
	public Float getSwitchProgram(Pausing pausing) {
		return this.getFloatMplayerProperty("switch_program", "switch_program", "ANS_switch_program=".length(), pausing);
	}
	
	public Float getSwitchProgram() {
		return getSwitchProgram(Pausing.non);
	}
	
	public void setSub(int sub, Pausing pausing) {
		setMplayerProperty("sub", ""+sub, pausing);
	}
	
	public void setSub(int sub) {
		setSub(sub, Pausing.non);
	}
	
	public Float getSub(Pausing pausing) {
		return this.getFloatMplayerProperty("sub", "sub", "ANS_sub=".length(), pausing);
	}
	
	public Float getSub() {
		return getSub(Pausing.non);
	}
	
	public void setSubSource(int sub_source, Pausing pausing) {
		setMplayerProperty("sub_source", ""+sub_source, pausing);
	}
	
	public void setSubSource(int sub_source) {
		setSubSource(sub_source, Pausing.non);
	}
	
	public Float getSubSource(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_source", "sub_source", "ANS_sub_source=".length(), pausing);
	}
	
	public Float getSubSource() {
		return getSubSource(Pausing.non);
	}
	
	public void setSubFile(int sub_file, Pausing pausing) {
		setMplayerProperty("sub_file", ""+sub_file, pausing);
	}
	
	public void setSubFile(int sub_file) {
		setSubFile(sub_file, Pausing.non);
	}
	
	public Float getSubFile(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_file", "sub_file", "ANS_sub_file=".length(), pausing);
	}
	
	public Float getSubFile() {
		return getSubFile(Pausing.non);
	}
	
	public void setSubVob(int sub_vob, Pausing pausing) {
		setMplayerProperty("sub_vob", ""+sub_vob, pausing);
	}
	
	public void setSubVob(int sub_vob) {
		setSubVob(sub_vob, Pausing.non);
	}
	
	public Float getSubVob(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_vob", "sub_vob", "ANS_sub_vob=".length(), pausing);
	}
	
	public Float getSubVob() {
		return getSubVob(Pausing.non);
	}
	
	public void setSubDemux(int sub_demux, Pausing pausing) {
		setMplayerProperty("sub_demux", ""+sub_demux, pausing);
	}
	
	public void setSubDemux(int sub_demux) {
		setSubDemux(sub_demux, Pausing.non);
	}
	
	public Float getSubDemux(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_demux", "sub_demux", "ANS_sub_demux=".length(), pausing);
	}
	
	public Float getSubDemux() {
		return getSubDemux(Pausing.non);
	}
	
	public void setSubDelay(float sub_delay, Pausing pausing) {
		setMplayerProperty("sub_delay", ""+sub_delay, pausing);
	}
	
	public void setSubDelay(float sub_delay) {
		setSubDelay(sub_delay, Pausing.non);
	}
	
	public Float getSubDelay(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_delay", "sub_delay", "ANS_sub_delay=".length(), pausing);
	}
	
	public Float getSubDelay() {
		return getSubDelay(Pausing.non);
	}
	
	public void setSubPos(int sub_pos, Pausing pausing) {
		setMplayerProperty("sub_pos", ""+sub_pos, pausing);
	}
	
	public void setSubPos(int sub_pos) {
		setSubPos(sub_pos, Pausing.non);
	}
	
	public Float getSubPos(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_pos", "sub_pos", "ANS_sub_pos=".length(), pausing);
	}
	
	public Float getSubPos() {
		return getSubPos(Pausing.non);
	}
	
	public void setSubAlignment(int sub_alignment, Pausing pausing) {
		setMplayerProperty("sub_alignment", ""+sub_alignment, pausing);
	}
	
	public void setSubAlignment(int sub_alignment) {
		setSubAlignment(sub_alignment, Pausing.non);
	}
	
	public Float getSubAlignment(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_alignment", "sub_alignment", "ANS_sub_alignment=".length(), pausing);
	}
	
	public Float getSubAlignment() {
		return getSubAlignment(Pausing.non);
	}
	
	public void setSubVisibility(int sub_visibility, Pausing pausing) {
		setMplayerProperty("sub_visibility", ""+sub_visibility, pausing);
	}
	
	public void setSubVisibility(int sub_visibility) {
		setSubVisibility(sub_visibility, Pausing.non);
	}
	
	public Float getSubVisibility(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_visibility", "sub_visibility", "ANS_sub_visibility=".length(), pausing);
	}
	
	public Float getSubVisibility() {
		return getSubVisibility(Pausing.non);
	}
	
	public void setSubForcedOnly(int sub_forced_only, Pausing pausing) {
		setMplayerProperty("sub_forced_only", ""+sub_forced_only, pausing);
	}
	
	public void setSubForcedOnly(int sub_forced_only) {
		setSubForcedOnly(sub_forced_only, Pausing.non);
	}
	
	public Float getSubForcedOnly(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_forced_only", "sub_forced_only", "ANS_sub_forced_only=".length(), pausing);
	}
	
	public Float getSubForcedOnly() {
		return getSubForcedOnly(Pausing.non);
	}
	
	public void setSubScale(float sub_scale, Pausing pausing) {
		setMplayerProperty("sub_scale", ""+sub_scale, pausing);
	}
	
	public void setSubScale(float sub_scale) {
		setSubScale(sub_scale, Pausing.non);
	}
	
	public Float getSubScale(Pausing pausing) {
		return this.getFloatMplayerProperty("sub_scale", "sub_scale", "ANS_sub_scale=".length(), pausing);
	}
	
	public Float getSubScale() {
		return getSubScale(Pausing.non);
	}
	
	public void setTVBrightness(int tv_brightness, Pausing pausing) {
		setMplayerProperty("tv_brightness", ""+tv_brightness, pausing);
	}
	
	public void setTVBrightness(int tv_brightness) {
		setTVBrightness(tv_brightness, Pausing.non);
	}
	
	public Float getTVBrightness(Pausing pausing) {
		return this.getFloatMplayerProperty("tv_brightness", "tv_brightness", "ANS_tv_brightness=".length(), pausing);
	}
	
	public Float getTVBrightness() {
		return getTVBrightness(Pausing.non);
	}
	
	public void setTVContrast(int tv_contrast, Pausing pausing) {
		setMplayerProperty("tv_contrast", ""+tv_contrast, pausing);
	}
	
	public void setTVContrast(int tv_contrast) {
		setTVContrast(tv_contrast, Pausing.non);
	}
	
	public Float getTVContrast(Pausing pausing) {
		return this.getFloatMplayerProperty("tv_contrast", "tv_contrast", "ANS_tv_contrast=".length(), pausing);
	}
	
	public Float getTVContrast() {
		return getTVContrast(Pausing.non);
	}
	
	public void setTVSaturation(int tv_saturation, Pausing pausing) {
		setMplayerProperty("tv_saturation", ""+tv_saturation, pausing);
	}
	
	public void setTVSaturation(int tv_saturation) {
		setTVSaturation(tv_saturation, Pausing.non);
	}
	
	public Float getTVSaturation(Pausing pausing) {
		return this.getFloatMplayerProperty("tv_saturation", "tv_saturation", "ANS_tv_saturation=".length(), pausing);
	}
	
	public Float getTVSaturation() {
		return getTVSaturation(Pausing.non);
	}
	
	public void setTVHue(int tv_hue, Pausing pausing) {
		setMplayerProperty("tv_hue", ""+tv_hue, pausing);
	}
	
	public void setTVHue(int tv_hue) {
		setTVHue(tv_hue, Pausing.non);
	}
	
	public Float getTVHue(Pausing pausing) {
		return this.getFloatMplayerProperty("tv_hue", "tv_hue", "ANS_tv_hue=".length(), pausing);
	}
	
	public Float getTVHue() {
		return getTVHue(Pausing.non);
	}
	
	public void setTeletextPage(int teletext_page, Pausing pausing) {
		setMplayerProperty("teletext_page", ""+teletext_page, pausing);
	}
	
	public void setTeletextPage(int teletext_page) {
		setTeletextPage(teletext_page, Pausing.non);
	}
	
	public Float getTeletextPage(Pausing pausing) {
		return this.getFloatMplayerProperty("teletext_page", "teletext_page", "ANS_teletext_page=".length(), pausing);
	}
	
	public Float getTeletextPage() {
		return getTeletextPage(Pausing.non);
	}
	
	public void setTeletextSubPage(int teletext_subpage, Pausing pausing) {
		setMplayerProperty("teletext_subpage", ""+teletext_subpage, pausing);
	}
	
	public void setTeletextSubPage(int teletext_subpage) {
		setTeletextSubPage(teletext_subpage, Pausing.non);
	}
	
	public Float getTeletextSubPage(Pausing pausing) {
		return this.getFloatMplayerProperty("teletext_subpage", "teletext_subpage", "ANS_teletext_subpage=".length(), pausing);
	}
	
	public Float getTeletextSubPage() {
		return getTeletextSubPage(Pausing.non);
	}
	
	public void setTeletextMode(int teletext_mode, Pausing pausing) {
		setMplayerProperty("teletext_mode", ""+teletext_mode, pausing);
	}
	
	public void setTeletextMode(int teletext_mode) {
		setTeletextMode(teletext_mode, Pausing.non);
	}
	
	public Float getTeletextMode(Pausing pausing) {
		return this.getFloatMplayerProperty("teletext_mode", "teletext_mode", "ANS_teletext_mode=".length(), pausing);
	}
	
	public Float getTeletextMode() {
		return getTeletextMode(Pausing.non);
	}
	
	public void setTeletextFormat(int teletext_format, Pausing pausing) {
		setMplayerProperty("teletext_format", ""+teletext_format, pausing);
	}
	
	public void setTeletextFormat(int teletext_format) {
		setTeletextFormat(teletext_format, Pausing.non);
	}
	
	public Float getTeletextFormat(Pausing pausing) {
		return this.getFloatMplayerProperty("teletext_format", "teletext_format", "ANS_teletext_format=".length(), pausing);
	}
	
	public Float getTeletextFormat() {
		return getTeletextFormat(Pausing.non);
	}
	
	public void setTeletextHalfPage(int teletext_half_page, Pausing pausing) {
		setMplayerProperty("teletext_half_page", ""+teletext_half_page, pausing);
	}
	
	public void setTeletextHalfPage(int teletext_half_page) {
		setTeletextHalfPage(teletext_half_page, Pausing.non);
	}
	
	public Float getTeletextHalfPage(Pausing pausing) {
		return this.getFloatMplayerProperty("teletext_half_page", "teletext_half_page", "ANS_teletext_half_page=".length(), pausing);
	}
	
	public Float getTeletextHalfPage() {
		return getTeletextHalfPage(Pausing.non);
	}	
	
	public void killMplayer() {
		mpp.forceStop();
	}	
}
