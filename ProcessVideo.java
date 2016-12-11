import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;


/*
 * Uses Xuggler to pull each image form 1000 to 4000, get its intensity,
 * load it into a matrix, and write that matrix to file.
 */
public class ProcessVideo
{

	int imageCount = 1;
	double intensityBins [] = new double [26];
	double intensityMatrix [][] = new double[4000][26];
	//intensity method 

		public void getIntensity(BufferedImage image, int height, int width){

			Color color = null;
			int intensity = 0;

			//All dimensions are placed in the first part of the row.
			intensityMatrix[imageCount - 1][0] = height * width;

			//Fills proper intensity bins with image attributes.
			for(int i = 0; i < height; i++) {
				for(int j = 0; j < width; j++) {
					color = new Color(image.getRGB(j, i));
					//Intensity equation
					intensity = (int) (.299*color.getRed() + .587*color.getGreen()
							+ 0.114*color.getBlue());
					if (intensity < 250) {
						intensityMatrix[imageCount - 1][(int) (intensity/10) + 1]++;
					}
					else {
						intensityMatrix[imageCount - 1][25]++;
					}
				}

			}
		}
	
	/**
	 * Takes a media container (file) as the first argument, opens it,
	 * plays audio as quickly as it can, and opens up a Swing window and displays
	 * video frames with <i>roughly</i> the right timing.
	 *  
	 * @param args Must contain one string which represents a filename
	 */
	@SuppressWarnings("deprecation")
	public static void main(String[] args)
	{
		ProcessVideo pv = new ProcessVideo();
		File video;
		String filename;
		if (args.length <= 0) {
			video = new File("20020924_juve_dk_02a.avi");
			filename = video.getAbsolutePath();
		}
		else {
			filename = args[0];
		}

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

		// and iterate through the streams to find the video stream
		int videoStreamId = -1;
		IStreamCoder videoCoder = null;
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
		}
		if (videoStreamId == -1)
			throw new RuntimeException("could not find video stream in container: "+filename);

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
		}


		/*
		 * Now, we start walking through the container looking at each packet.
		 */
		IPacket packet = IPacket.make();
		int count = 0;

		container.seekKeyFrame(0, 999, 1000, 1001, IContainer.SEEK_FLAG_FRAME);
		while(count < 4000)
		{
			container.readNextPacket(packet);
			//    	System.out.println(packet.getPosition());
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


					BufferedImage img = new BufferedImage(newPic.getWidth(), newPic.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
					IConverter converter = ConverterFactory.createConverter(ConverterFactory.XUGGLER_BGR_24, newPic);
					img = converter.toImage(newPic);
					pv.getIntensity(img, img.getHeight(), img.getWidth());
					
					pv.imageCount++;
					count++;
				}
			}
		}
		if (videoCoder != null)
		{
			videoCoder.close();
			videoCoder = null;
		}
		if (container !=null)
		{
			container.close();
			container = null;
		}
		
		pv.writeIntensity(filename);
	}


	//This method writes the contents of the intensity matrix to a file called intensity.txt
	public void writeIntensity(String filename){
		/////////////////////
		///your code///
		/////////////////
		File file;
		BufferedWriter writer = null;

		try {
			file = new File(filename + "_frames.txt");

			if(!file.exists()) {
				file.createNewFile();
			}

			writer = new BufferedWriter(new FileWriter(file));

			//Goes through the intensityMatrix and divides each attribute with a comma
			//Also ensures no comma before the first value or after the last value.
			String comma = "";
			for(double[] i: intensityMatrix) {
				for(double j: i) {
					writer.write(comma + String.valueOf((int) j));
					comma = ",";
				}
			}

			writer.close();
		}

		catch (IOException e) {

			System.out.println("File could not be created.");
		}
	}
}

