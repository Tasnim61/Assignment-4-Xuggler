import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/*
 * This class holds the GUI for the application.
 * It is called by the video control class to display contents to the
 * user, and it sends notification to the main thread of the VideoControl object.
 */
@SuppressWarnings("serial")
public class VideoImage extends JFrame {
	private JPanel top1;
	private JList<VideoFrame> bottom1;		//Holder for all of our frames
	private JLabel videoInfo;				//Will display info on the current shot
	private JPanel topright;
	JButton playButton;						//Can play the video here.
	private JScrollPane frameHolder;		//Added so make the selection frames scrollable
	private int videoStartFrame;
	private int videoEndFrame;
	public boolean changed;
	public boolean paused;
	
    private final ImagePane mOnscreenPicture;	//Where the video will be played.

    public VideoImage() {
        super();
        mOnscreenPicture = new ImagePane();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        paused = true;		//On starting pause the video
        changed = false;
        
		setTitle("Frame Decomposer");
		
		top1 = new JPanel();
		bottom1 = new JList<VideoFrame>();
		videoInfo = new JLabel();
		topright = new JPanel();
		JLabel instructions = new JLabel();			//Used to provide instruction to the user.
		frameHolder = new JScrollPane(bottom1);
		frameHolder.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		GridLayout gridlayout1 = new GridLayout(2, 1, 5, 5);
		GridLayout gridlayout2 = new GridLayout(1, 2, 5, 5);
		GridLayout gridlayout3 = new GridLayout(3, 1, 5, 5);
		topright.setLayout(gridlayout3);
		setLayout(gridlayout1);
		top1.setLayout(gridlayout2);
		bottom1.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		bottom1.setVisibleRowCount(-1);
		videoInfo.setLayout(new BorderLayout());
		
		add(top1);
		add(frameHolder);
		
		instructions.setText("<html>Click on the shot in the Left Panel to play/pause."
				+ "<br />Select shot from bottom panel in order to view it."
				+ "<br />Upon shot selection the view will automatically start at the beginning"
				+ "<br />and you must click it to begin.<html>");
		
		videoInfo.setText("<html>Start Frame is: 1000<br />Last Frame is: 1090<html>");
		
		mOnscreenPicture.addMouseListener(new playHandler());
		
		playButton = new JButton("Play");
		
		top1.add(mOnscreenPicture);
		top1.add(topright);
		topright.add(instructions);
		topright.add(videoInfo);
		topright.add(playButton);
		
		playButton.addActionListener(new playButtonHandler());
		
		
        setSize(1100, 750);
		// this centers the frame on the screen
		setLocationRelativeTo(null);
    }

    //Used to change the image displayed in the GUI.
    public void setImage(final BufferedImage aImage) {
        mOnscreenPicture.setImage(aImage);
    }

    
    //Class that holds the playing video, Size of video adjust based
    //on window size.
    public class ImagePane extends JPanel {

        private BufferedImage mImage;

        public void setImage(BufferedImage image) {
            SwingUtilities.invokeLater(new ImageRunnable(image));
        }

        @Override
        public Dimension getPreferredSize() {
            return mImage == null ? new Dimension(200, 200) : new Dimension(mImage.getWidth(), mImage.getHeight());
        }

        private class ImageRunnable implements Runnable {

            private final BufferedImage newImage;

            public ImageRunnable(BufferedImage newImage) {
                super();
                this.newImage = newImage;
            }

            @Override
            public void run() {    	
                ImagePane.this.mImage = newImage;
                Dimension size = getPreferredSize();
                final Dimension newSize = new Dimension(mImage.getWidth(), mImage.getHeight());
                if (!newSize.equals(size)) {
                    VideoImage.this.invalidate();
                    VideoImage.this.revalidate();
                    VideoImage.this.pack();
                    repaint();
                }
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (mImage != null) {
                g.drawImage(mImage, 0, 0, getWidth(), getHeight(), this);
            }
        }
        
    }
    
    /*
     * Custom renderer for JList. Renders the VideoFrame class to pull the information
     * and post it into the GUI.
     */
    public class FrameRenderer extends JLabel implements ListCellRenderer<VideoFrame> {
    	 
        @Override
        public Component getListCellRendererComponent(JList<? extends VideoFrame> list, VideoFrame frame, int index,
            boolean isSelected, boolean cellHasFocus) {
            
        	Image newimg = frame.image.getScaledInstance(200, 200,  java.awt.Image.SCALE_FAST);
    		ImageIcon icon = new ImageIcon(newimg);
        	this.setIcon(icon);
        	this.setText("Frame " + frame.getFrameNum() + " at time " + frame.getTimestamp());
        	this.setHorizontalTextPosition(CENTER);
        	this.setVerticalTextPosition(BOTTOM);
        	
        	this.setLayout(new BorderLayout());
        	this.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
             
            return this;
        }
         
    }
    
    /*
     * Places the given frames that have been placed into VideoFrame object into the
     * GUI at the bottom.
     */
    public void setFrameIcons(ArrayList<VideoFrame> here) {
    	DefaultListModel<VideoFrame> listModel = new DefaultListModel<VideoFrame>();
    	
    	//This is the listener for when new images are selected for their shots
    	//to be played.
    	bottom1.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent arg0) {
				if (!arg0.getValueIsAdjusting()) {
					@SuppressWarnings("unchecked")
					JList<VideoFrame> source = (JList<VideoFrame>)arg0.getSource();
					mOnscreenPicture.setImage(source.getSelectedValue().getImage());
					videoStartFrame = source.getSelectedValue().getFrameNum();
					//If its the last shot, set last frame manually, otherwise
					//make it the next highest frame we have.
					if(source.getSelectedIndex() + 1 == here.size()) {
						videoEndFrame = 4999;
					}
					else {
						videoEndFrame = here.get(source.getSelectedIndex() + 1).getFrameNum();
					}
					
					videoInfo.removeAll();
					videoInfo.setText("<html>Start Frame is: " + videoStartFrame
							+ "<br />Last Frame is: " + videoEndFrame + "<html>");
					changed = true;
					
					videoInfo.revalidate();  
					videoInfo.repaint();
	            }
			}
        });
    	bottom1.setCellRenderer(new FrameRenderer());
    	for(VideoFrame frame: here) {
    		listModel.addElement(frame);
    	}
    	bottom1.setModel(listModel);
    	videoStartFrame = here.get(0).getFrameNum();
    	videoEndFrame = here.get(1).getFrameNum();
    	mOnscreenPicture.setImage(here.get(0).getImage());
    	VideoImage.this.invalidate();
        VideoImage.this.revalidate();
        repaint();
        
        this.setVisible(true);
        
    }
    
    /*
     * Listener for out play button.
     */
    private class playButtonHandler implements ActionListener {

		public void actionPerformed( ActionEvent e) {
			if(paused) {
				((JButton) e.getSource()).setText("Pause");
				paused = false;
				VideoControl.threadnoti();
			}
			else {
				((JButton) e.getSource()).setText("Play");
				paused = true;
			}
		}
	}
    
    /*
     * Listener for when the video in the play panel is clicked.
     */
    private class playHandler implements MouseListener {
		@Override
		public void mouseClicked(MouseEvent arg0) {
			if(paused) {
				playButton.setText("Pause");
				paused = false;
				VideoControl.threadnoti();
			}
			else {
				playButton.setText("Play");
				paused = true;
			}
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			
		}

		@Override
		public void mouseExited(MouseEvent e) {
			
		}

		@Override
		public void mousePressed(MouseEvent e) {
			
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			
		}
		
	}
    
    //Changed getter
    public boolean isChanged() {
    	return changed;
    }
    
    //Paused getter
    public boolean isPaused() {
    	return paused ;
    }
    
    //Find what the current end frame is.
    public int getEndFrame() {
    	return videoEndFrame;
    }
    
    //Find what the current start frame is.
    public int getStartFrame() {
    	return videoStartFrame;
    }
    
    //Sets paused to true, and sets play button to
    //"Play" no matter what.
    public void setPaused() {
    	playButton.setText("Play");
    	paused = true;
    }
}