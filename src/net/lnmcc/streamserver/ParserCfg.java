package net.lnmcc.streamserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class ParserCfg {
	private String ffCfgPath;
	private File ffCfgFile;
	private FFserverCfg ffservercfg;

	private State commonState;
	private State feedState;
	private State streamState;
	private State state;

	private FFserver ffserver;

	public ParserCfg(String path) {
		commonState = new CommonState(this);
		feedState = new FeedState(this);
		streamState = new StreamState(this);
		state = commonState;

		ffservercfg = new FFserverCfg();

		if (path == null) {
			// use default
			ffCfgPath = String.valueOf("/etc/ffserver.conf");
		} else {
			ffCfgPath = path;
		}
		ffserver = FFserver.getFFserver();
		ffserver.setPath(ffCfgPath);
	}

	public void printCfg() {
		ffservercfg.printCfg();
	}

	State getCommonState() {
		return commonState;
	}

	State getFeedState() {
		return feedState;
	}

	State getStreamState() {
		return streamState;
	}

	void setState(State st) {
		state = st;
	}

	void addCommon(String key, String val) {
		ffservercfg.addCommon(key, val);
	}

	void addFeed(String name, Feed feed) {
		ffservercfg.addFeed(name, feed);
	}

	void addStream(String name, Stream stream) {
		ffservercfg.addStream(name, stream);
	}

	void classify(String line) {
		state.classify(line);
	}

	void addFeedSection(String name) {
		ffservercfg.addFeedSection(name);
	}

	void addStreamSection(String name) {
		ffservercfg.addStreamSection(name);
	}

	/**
	 * write this ffserver.conf to file
	 * 
	 * @param path
	 *            which you want to save this configure
	 * @return void
	 */
	public void writeCfg(String path) {
		PrintWriter pw = null;
		File dstFile;
		try {
			dstFile = new File(path);
			if (!dstFile.exists())
				dstFile.createNewFile();

			pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
					dstFile)), true);
			/* empty the file */
			pw.print(String.valueOf(""));
			ffservercfg.writeCfg(pw);

		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
	}

	/**
	 * 增加一个新的流，如果已经加过了就不要在加入配置文件，我会取rtsp流地址的ip:host作为该流的唯一标志。
	 * 增加一个新的流会导致重启ffserver和所有已注册的ffmpeg。
	 * ffmpeg的输出地址一定是http://localhost:8090/{identity}.ffm 。
	 * 
	 * @param rtspUrl
	 *            rtsp的流地址 比如：
	 *            rtsp://192.168.2.191:554/user=admin&password=admin
	 *            &channel=1&stream=0.sdp
	 * @return
	 */
	public void addNewStream(String rtspUrl) {
		if (!rtspUrl.startsWith("rtsp://")) {
			throw new IllegalArgumentException("Error rtsp url");
		}

		String str = rtspUrl.substring(rtspUrl.indexOf("rtsp://") + 7,
				rtspUrl.lastIndexOf('/'));
		String identity = str.replace('.', '-').replace(':', '_');
		if (ffservercfg.isExist(identity)) {
			System.out.println("Exist"); // FIXME

		} else {
			addFeedSection(identity);
			addStreamSection(identity);
			writeCfg(ffCfgPath);

			/* Restart ffserver */
			ffserver.stop();
			ffserver.start();
			/* 注册一个新的ffmpeg用于新的流 */
			ffserver.addFFmpeg(rtspUrl, "http://localhost:8090/" + identity
					+ ".ffm");
		}
	}

	/**
	 * 删除一个流首先会停止这个流，然后把与该流相关的信息从ffserver.conf中删除。 这个方法会导致ffserver重启。
	 * ffserver会自动重启他说有注册过的ffmpeg 。
	 * 
	 * @param rtspUrl
	 */
	public void deleteStream(String rtspUrl) {
		if (!rtspUrl.startsWith("rtsp://")) {
			throw new IllegalArgumentException("Error rtsp url");
		}

		stopStream(rtspUrl);

		String str = rtspUrl.substring(rtspUrl.indexOf("rtsp://") + 7,
				rtspUrl.lastIndexOf('/'));
		String identity = str.replace('.', '-').replace(':', '_');

		if (!ffservercfg.isExist(identity))
			return;

		ffservercfg.deleteFeed(identity + ".ffm");
		ffservercfg.deleteStream(identity + ".rtp");
		writeCfg(ffCfgPath);
		ffserver.stop();
		ffserver.start();
	}

	/**
	 * 停止一个流只是取消该流在ffmserver中的注册并关闭对应的ffmpeg。不会把ffserver.conf中对应的section删除。
	 * 
	 * @param rtspUrl
	 */
	public void stopStream(String rtspUrl) {
		if (!rtspUrl.startsWith("rtsp://")) {
			throw new IllegalArgumentException("Error rtsp url");
		}

		ffserver.deleteFFmpeg(rtspUrl);
	}

	public void parse() {
		ffCfgFile = new File(ffCfgPath);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(ffCfgFile));
			String line = null;

			while ((line = reader.readLine()) != null) {
				// skip comment line and space line
				if (!line.startsWith(String.valueOf("#")) && !line.isEmpty()
						&& !line.matches("^\\s*\n$") && !line.matches("\\s*$"))
					classify(line);
			}

			reader.close();

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		final String rtspUrl = "rtsp://192.168.2.191:554/user=admin&password=admin&channel=1&stream=0.sdp";
		ParserCfg parser = new ParserCfg(
				"/home/sijiewang/MyDisk/Projects/stream-media-test/ff.conf");
		parser.parse();
		// parser.printCfg();
		parser.addNewStream(rtspUrl);

		// parser.addNewStream(String.valueOf("rtsp://192.168.2.211:5554/tv.rtp"));
		// parser.writeCfg(String.valueOf("/home/sijiewang/MyDisk/Projects/stream-media-test/ff.conf"));

		try {
			Thread.sleep(20 * 1000);
			parser.stopStream(rtspUrl);
			Thread.sleep(2 * 1000);
			parser.deleteStream(rtspUrl);
			Thread.sleep(2 * 1000);
			parser.addNewStream(rtspUrl);

		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}
}