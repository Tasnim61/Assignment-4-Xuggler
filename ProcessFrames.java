import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

/*
 * This class reads the video feature matrix from a file and uses
 * Twin-comparison approach to find the beginnings of each shot.
 */
public class ProcessFrames {

	private Double [][] frameMatrix = new Double [4000][26];
	private double tb = 0;			//threshold for cut
	private double ts = 0;			//threshold for transition
	private int tor = 2;			//frame stop threshold
	private ArrayList<Integer> firstFrames = new ArrayList<Integer>();

	ProcessFrames(String filename) {
		readFrameFile(filename + "_frames.txt");
		findFirstFrames();
	}

	
	public void readFrameFile(String filename){
		Scanner read = null;
		try{
			read =new Scanner(new File (filename));
			read.useDelimiter(",");
			/////////////////////
			///your code///
			/////////////////


			/* loops through the files and constructs a matrix with
			 * intensity values.
			 */
			for(int i = 0; i < frameMatrix.length; i++) {
				for(int j = 0; j < frameMatrix[0].length; j++) {
					frameMatrix[i][j] = read.nextDouble();
				}
			}
		}
		catch(FileNotFoundException EE){
			System.out.println("The file " + filename + " does not exist");
		}
		finally {
			read.close();
		}
	}

	//Finds the distance between each frame, puts it in an array and uses that array
	//to find the cuts and transitions, then sorts them by their frame values.
	private void findFirstFrames() {
		double [] distance = new double[frameMatrix.length];
		ArrayList<Integer> cuts = new ArrayList<Integer>();
		ArrayList<Integer> transitions = new ArrayList<Integer>();


		distance = findDistance(frameMatrix);

		cuts = findCuts(distance);
		transitions = findTransitions(distance);

		firstFrames.addAll(cuts);
		firstFrames.addAll(transitions);

		firstFrames.sort(null);
	}

	//Cycles through distances and if the value is greater than or equal
	//to the cut threshold, add it to the cuts.
	private ArrayList<Integer> findCuts(double[] distance) {
		ArrayList<Integer> results = new ArrayList<Integer>();
		for(int i = 1; i < distance.length; i++) {
			if(distance[i] >= tb) {
				//Have to add 1 because Ce is frame i + 1
				results.add(i + 1 + 1000);
			}
		}
		return results;
	}

	/*
	 * Cycles through the distances of the value is between tb and ts
	 * set fscan and change the count of the values counted under ts to 0.
	 * If value is greater than tb restart the count. If the value is below
	 * ts, increment the under count, and if that ever = tor, we check and see
	 * if the sum of our differences in frame is > tb.
	 */
	private ArrayList<Integer> findTransitions(double[] distance) {
		ArrayList<Integer> results = new ArrayList<Integer>();
		int fscan = 0;
		int fecan = 0;
		int under = 0;
		for(int i = 1; i < distance.length; i++) {
			if(ts <= distance[i] && distance[i] < tb) {
				if(fscan == 0) {
					fscan = i;
				}
				under = 0;
			}
			else {
				if(distance[i] > tb) {
					fscan = 0;
				}
				else if(ts > distance[i] && fscan != 0) {
					under++;
					if(under == tor) {
						fecan = i;
						int sum = 0;
						//Goes from fscan - 1 to pick up frame 3277.
						for(int j = fscan - 1; j < fecan; j++) {
							sum += distance[j];
						}
						if (sum >= tb) {
							results.add(fscan + 2 + 1000);
						}
						fscan = 0;
					}
				}
			}
		}
		return results;
	}

	public ArrayList<Integer> getFirstFrames() {
		return firstFrames;
	}
	/*
	 * This method takes a matrix and the index in that matrix where picture features
	 * start, it then calculates the distance from each picture, and adds the value of the
	 * picture index to a Hashmap with the distance as the key. It returns a Double Array
	 * that contains all of the distances.
	 */
	private double[] findDistance(Double[][] matrix) {
		//Extending results matrix by 1 so that frame counts can
		//start at 1 instead of 0 when finding first frames.
		double[] results = new double[matrix.length + 1];
		int compareImage = 1;
		int picFeature;
		double d = 0;
		while(compareImage < matrix.length) {
			d = 0;
			picFeature = 1;
			while(picFeature < matrix[0].length) {
				d += Math.abs((matrix[compareImage][picFeature])
						-(matrix[compareImage - 1][picFeature]));
				picFeature++;
			}
			results[compareImage] = d;
			compareImage++;
		}
		double mean = calcMean(results);
		double std = calcStd(results);

		tb = mean + std*11;
		ts = mean*2;

		return results;
	}

	//Method to find the mean of the given double array
	//and returns it in a double.
	private double calcMean(double[] values) {
		double sum = 0;
		for(int j = 1; j < values.length - 1; j++) {
			sum += values[j];
		}
		return (double) sum/(values.length - 2);
	}


	//Method to find the standard deviation of the given double array
	//and returns it in a double.
	private double calcStd(double[] values) {
		double deviation = 0;
		double mean = calcMean(values);
		for(int i = 1; i < values.length - 1; i++) {
			deviation += Math.pow(values[i] - mean, 2);
		}
		return (double) Math.sqrt(deviation/(values.length - 3));
	}
}