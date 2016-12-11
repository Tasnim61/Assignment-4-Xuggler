import java.awt.image.BufferedImage;

/*
 * Custom class to place a frame's image, frameNum, and timestamp all together.
 */
public class VideoFrame {
	BufferedImage image;
	int frameNum;
	String timestamp;
	
	//Takes a bufferedImage, frame, and timestamp.
	VideoFrame(BufferedImage image, int frameNum, String timestamp) {
		this.image = image;
		this.frameNum = frameNum;
		this.timestamp = timestamp;
	}
	
	public BufferedImage getImage() {
		return image;	
	}
	
	public int getFrameNum() {
		return frameNum;
	}
	
	public String getTimestamp() {
		return timestamp;
	}
}