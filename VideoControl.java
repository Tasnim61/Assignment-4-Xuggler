import java.io.File;
import java.util.ArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.Utils;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * Takes a media container, finds the first video stream,
 * decodes that stream, and then plays the audio and video.
 *
 * This code does a VERY coarse job of matching time-stamps, and thus
 * the audio and video will float in and out of slight sync.  Getting
 * time-stamps syncing-up with audio is very system dependent and left
 * as an exercise for the reader.
 * 
 * @author aclarke
 * @author Jeremy Woods
 *
 */
public class VideoControl
{

	/**
	 * The audio line we'll output sound to; it'll be the default audio device on your system if available
	 */
	private static SourceDataLine mLine;

	/**
	 * The window we'll draw the video on.
	 * Calls a created class that handles the GUI
	 */
	private static VideoImage mScreen = null;

	private static long mSystemVideoClockStartTime;

	private static long mFirstVideoTimestampInStream;

	/*
	 * Array List of VideoFrame class that will hold all of the information for
	 * each frame that is the beginning or end of a shot.
	 */
	private static ArrayList<VideoFrame> firstFrames = new ArrayList<VideoFrame>();

	/*
	 * Used for thread locking in order to pause and play video.
	 */
	private static Thread threadObject;

	/**
	 * Takes a media container (file) as the first argument, opens it,
	 * plays audio as quickly as it can, and opens up a Swing window and displays
	 * video frames with <i>roughly</i> the right timing.
	 *  
	 * @param args Must contain one string which represents a filename
	 */
	@SuppressWarnings({ "deprecation", "static-access" })
	public static void main(String[] args)
	{

		//Start our thread.
		setThreadObject(new Thread());
		getThreadObject().start();

		//Has the ability to take file input as long as file is the full path.
		//there is no error checking here.
		File video;
		String filename;
		    if (args.length <= 0) {
		    		video = new File("20020924_juve_dk_02a.avi");
		    		filename = video.getAbsolutePath();
		    }
		    else {
		    	filename = args[0];
		    }

		 //Custom class that uses the twins algorithm to find the cut frames.
		ProcessFrames pf = new ProcessFrames(filename);

		
		/*Xuggler code*/
		// Let's make sure that we can actually convert video pixel formats.
		if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
			throw new RuntimeException("you must install the GPL version of Xuggler (with IVideoResampler support) for this demo to work");

		// Create a Xuggler container object
		IContainer container = IContainer.make();

		// Open up the container
		if (container.open(filename, IContainer.Type.READ, null) < 0)
			throw new IllegalArgumentException("could not open file: " + filename);

		// query how many streams the call to open found
		int numStreams = container.getNumStreams();

		// and iterate through the streams to find the first audio stream
		int videoStreamId = -1;
		IStreamCoder videoCoder = null;
		int audioStreamId = -1;
		IStreamCoder audioCoder = null;
		for(int i = 0; i < numStreams; i++)
		{
			// Find the stream object
			IStream stream = container.getStream(i);
			// Get the pre-configured decoder that can decode this stream;
			IStreamCoder coder = stream.getStreamCoder();

			if (videoStreamId == -1 && coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO)
			{
				videoStreamId = i;
				videoCoder = coder;
			}
			else if (audioStreamId == -1 && coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO)
			{
				audioStreamId = i;
				audioCoder = coder;
			}
		}
		if (videoStreamId == -1 && audioStreamId == -1)
			throw new RuntimeException("could not find audio or video stream in container: "+filename);

		/*
		 * Check if we have a video stream in this file.  If so let's open up our decoder so it can
		 * do work.
		 */
		IVideoResampler resampler = null;
		if (videoCoder != null)
		{
			if(videoCoder.open() < 0)
				throw new RuntimeException("could not open audio decoder for container: "+filename);

			if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24)
			{
				// if this stream is not in BGR24, we're going to need to
				// convert it.  The VideoResampler does that for us.
				resampler = IVideoResampler.make(videoCoder.getWidth(), videoCoder.getHeight(), IPixelFormat.Type.BGR24,
						videoCoder.getWidth(), videoCoder.getHeight(), videoCoder.getPixelType());
				if (resampler == null)
					throw new RuntimeException("could not create color space resampler for: " + filename);
			}
			/*
			 * And once we have that, we draw a window on screen
			 */
			openJavaVideo();
		}

		if (audioCoder != null)
		{
			if (audioCoder.open() < 0)
				throw new RuntimeException("could not open audio decoder for container: "+filename);

			/*
			 * And once we have that, we ask the Java Sound System to get itself ready.
			 */
			try
			{
				openJavaSound(audioCoder);
			}
			catch (LineUnavailableException ex)
			{
				throw new RuntimeException("unable to open sound device on your system when playing back container: "+filename);
			}
		}

		/*End of Xuggler Code */

		/*
		 * Now, we start walking through the container looking at each packet.
		 */
		IPacket packet = IPacket.make();
		//Place frame 100 at the beginning of our frames so that we can have a
		//start frame for it as well.
		pf.getFirstFrames().add(0, 1000);
		firstFrames = getFrameImage(pf.getFirstFrames(), container, packet, 
				videoCoder, resampler, videoStreamId, filename);

		//Set icons for the frames in our GUI.
		mScreen.setFrameIcons(firstFrames);

		/*
		 * This loop is here because it allows to use the application an unlimited amount of times
		 * even though the video is read in frame by frame.
		 */
		while(true) {
			//Set the count to the start of our current shot.
			int count = mScreen.getStartFrame();
			
			//Xuggler uses this to slow down decoding of the video frames.
			mFirstVideoTimestampInStream = Global.NO_PTS;
			mSystemVideoClockStartTime = 0;
			
			//Use this method to seek to the specified starting frame.
			container.seekKeyFrame(0, count - 1, count, count + 1, IContainer.SEEK_FLAG_ANY);

			//We only want go from the beginning of the shot to the end of the shot.
			while(count < mScreen.getEndFrame())
			{
				//Check if another picture was selected.
				//If true, change the variable and break.
				if(mScreen.isChanged()) {
					mScreen.changed = false;
					break;
				}
				
				//Check if the video has been paused or not.
				if(mScreen.isPaused()) {
					synchronized(getThreadObject())
					{
						// Pause
						try 
						{
							getThreadObject().wait();
						} 
						catch (InterruptedException e) 
						{
						}
					}
				}
				
				container.readNextPacket(packet);
				/*
				 * Now we have a packet, let's see if it belongs to our video stream
				 */

				if (packet.getStreamIndex() == videoStreamId)
				{
					/*
					 * We allocate a new picture to get the data out of Xuggler
					 */
					IVideoPicture picture = IVideoPicture.make(videoCoder.getPixelType(),
							videoCoder.getWidth(), videoCoder.getHeight());

					/*
					 * Now, we decode the video, checking for any errors.
					 * 
					 */
					int bytesDecoded = videoCoder.decodeVideo(picture, packet, 0);
					if (bytesDecoded < 0)
						throw new RuntimeException("got error decoding audio in: " + filename);

					/*
					 * Some decoders will consume data in a packet, but will not be able to construct
					 * a full video picture yet.  Therefore you should always check if you
					 * got a complete picture from the decoder
					 */
					if (picture.isComplete())
					{
						IVideoPicture newPic = picture;
						/*
						 * If the resampler is not null, that means we didn't get the video in BGR24 format and
						 * need to convert it into BGR24 format.
						 */
						if (resampler != null)
						{
							// we must resample
							newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
							if (resampler.resample(newPic, picture) < 0)
								throw new RuntimeException("could not resample video from: " + filename);
						}
						if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
							throw new RuntimeException("could not decode video as BGR 24 bit data in: " + filename);

						long delay = millisecondsUntilTimeToDisplay(newPic);
						// if there is no audio stream; go ahead and hold up the main thread.  We'll end
						// up caching fewer video pictures in memory that way.
						try
						{
							synchronized(getThreadObject()) {
								if (delay > 0)
									getThreadObject().sleep(delay);
							}
						}
						catch (InterruptedException e)
						{
							return;
						}

						// And finally, convert the picture to an image and display it
						count++;
						
						//If we made it to the end of the shot, pause
						if (count == mScreen.getEndFrame()) {
							mScreen.setPaused();
						}
						
						//If the shot selected shot is changed between the beginning
						//and this point in the method check it again.
						if(mScreen.isChanged()) {
							mScreen.changed = false;
							mScreen.setPaused();
							break;
						}

						mScreen.setImage(Utils.videoPictureToImage(newPic));

					}
				}
				else if (packet.getStreamIndex() == audioStreamId)
				{
					/*
					 * We allocate a set of samples with the same number of channels as the
					 * coder tells us is in this buffer.
					 * 
					 * We also pass in a buffer size (1024 in our example), although Xuggler
					 * will probably allocate more space than just the 1024 (it's not important why).
					 */
					IAudioSamples samples = IAudioSamples.make(1024, audioCoder.getChannels());

					/*
					 * A packet can actually contain multiple sets of samples (or frames of samples
					 * in audio-decoding speak).  So, we may need to call decode audio multiple
					 * times at different offsets in the packet's data.  We capture that here.
					 */
					int bytesDecoded = 144;
					int offset = 0;

					/*
					 * Keep going until we've processed all data
					 */
					
					//removed error checking because it allowed the code to avoid
					//negative bytesDecoded and therefore the video continutes fine.
					bytesDecoded = audioCoder.decodeAudio(samples, packet, offset);
					offset += bytesDecoded;
					/*
					 * Some decoder will consume data in a packet, but will not be able to construct
					 * a full set of samples yet.  Therefore you should always check if you
					 * got a complete set of samples from the decoder
					 */
					if (samples.isComplete())
					{
						// note: this call will block if Java's sound buffers fill up, and we're
						// okay with that.  That's why we have the video "sleeping" occur
						// on another thread.
											playJavaSound(samples);
					}
				}

			}
			
		}
	}

	private static long millisecondsUntilTimeToDisplay(IVideoPicture picture)
	{
		/**
		 * We could just display the images as quickly as we decode them, but it turns
		 * out we can decode a lot faster than you think.
		 * 
		 * So instead, the following code does a poor-man's version of trying to
		 * match up the frame-rate requested for each IVideoPicture with the system
		 * clock time on your computer.
		 * 
		 * Remember that all Xuggler IAudioSamples and IVideoPicture objects always
		 * give timestamps in Microseconds, relative to the first decoded item.  If
		 * instead you used the packet timestamps, they can be in different units depending
		 * on your IContainer, and IStream and things can get hairy quickly.
		 */
		long millisecondsToSleep = 0;
		if (mFirstVideoTimestampInStream == Global.NO_PTS)
		{
			// This is our first time through
			mFirstVideoTimestampInStream = picture.getTimeStamp();
			// get the starting clock time so we can hold up frames
			// until the right time.
			mSystemVideoClockStartTime = System.currentTimeMillis();
			millisecondsToSleep = 0;
		} else {
			long systemClockCurrentTime = System.currentTimeMillis();
			long millisecondsClockTimeSinceStartofVideo = systemClockCurrentTime - mSystemVideoClockStartTime;
			// compute how long for this frame since the first frame in the stream.
			// remember that IVideoPicture and IAudioSamples timestamps are always in MICROSECONDS,
			// so we divide by 1000 to get milliseconds.
			long millisecondsStreamTimeSinceStartOfVideo = (picture.getTimeStamp() - mFirstVideoTimestampInStream)/1000;
			final long millisecondsTolerance = 50; // and we give ourselfs 50 ms of tolerance
			millisecondsToSleep = (millisecondsStreamTimeSinceStartOfVideo -
					(millisecondsClockTimeSinceStartofVideo+millisecondsTolerance));
		}
		return millisecondsToSleep;
	}

	/**
	 * Opens a Swing window on screen.
	 */
	private static void openJavaVideo()
	{
		mScreen = new VideoImage();
	}

	private static void openJavaSound(IStreamCoder aAudioCoder) throws LineUnavailableException
	{
		AudioFormat audioFormat = new AudioFormat(aAudioCoder.getSampleRate(),
				(int)IAudioSamples.findSampleBitDepth(aAudioCoder.getSampleFormat()),
				aAudioCoder.getChannels(),
				true, /* xuggler defaults to signed 16 bit samples */
				false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		mLine = (SourceDataLine) AudioSystem.getLine(info);
		/**
		 * if that succeeded, try opening the line.
		 */
		mLine.open(audioFormat);
		/**
		 * And if that succeed, start the line.
		 */
		mLine.start();


	}

	private static void playJavaSound(IAudioSamples aSamples)
	{
		/**
		 * We're just going to dump all the samples into the line.
		 */
		byte[] rawBytes = aSamples.getData().getByteArray(0, aSamples.getSize());
		mLine.write(rawBytes, 0, aSamples.getSize());
	}

	//Process the frames to get the beginning and end of each shot.
	private static ArrayList<VideoFrame> getFrameImage(ArrayList<Integer> frameNum, IContainer container, IPacket packet, 
			IStreamCoder videoCoder, IVideoResampler resampler, int videoStreamId, String filename) {
		ArrayList<VideoFrame> result = new ArrayList<VideoFrame>();
		int count = 0;

		container.seekKeyFrame(0, 999, 1000, 1001, IContainer.SEEK_FLAG_FRAME);

		/*
		 * Seek only the frames from 1000 to the next 4000 frames so 1000 - 5000.
		 * This method does the same as playing it above except when we find a frame that 
		 * matches one of the frameNumber we have determined to be a firstFrame, we add
		 * the image, the frame num, and the timestamp to a VideoFrame arraylist.
		 */
		
		while(count < 4000)
		{
			container.readNextPacket(packet);
			if (packet.getStreamIndex() == videoStreamId)
			{
				/*
				 * We allocate a new picture to get the data out of Xuggler
				 */
				IVideoPicture picture = IVideoPicture.make(videoCoder.getPixelType(),
						videoCoder.getWidth(), videoCoder.getHeight());

				/*
				 * Now, we decode the video, checking for any errors.
				 * 
				 */
				int bytesDecoded = videoCoder.decodeVideo(picture, packet, 0);
				if (bytesDecoded < 0)
					throw new RuntimeException("got error decoding video in: " + filename);

				/*
				 * Some decoders will consume data in a packet, but will not be able to construct
				 * a full video picture yet.  Therefore you should always check if you
				 * got a complete picture from the decoder
				 */
				if (picture.isComplete())
				{
					IVideoPicture newPic = picture;
					/*
					 * If the resampler is not null, that means we didn't get the video in BGR24 format and
					 * need to convert it into BGR24 format.
					 */
					if (resampler != null)
					{
						// we must resample
						newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
						if (resampler.resample(newPic, picture) < 0)
							throw new RuntimeException("could not resample video from: " + filename);
					}
					if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
						throw new RuntimeException("could not decode video as BGR 24 bit data in: " + filename);

					if(frameNum.contains(count + 1000)) {
						IConverter converter = ConverterFactory.createConverter(ConverterFactory.XUGGLER_BGR_24, newPic);
						result.add(new VideoFrame(converter.toImage(newPic), count + 1000, newPic.getFormattedTimeStamp()));	
					}

					count++;
				}  
			}
		}

		return result;
	}

	public static Thread getThreadObject() {
		return threadObject;
	}

	public static void setThreadObject(Thread threadObject) {
		VideoControl.threadObject = threadObject;
	}
	
	public static void threadnoti() {
		synchronized(getThreadObject())
		{
				getThreadObject().notify();

		}
	}
}