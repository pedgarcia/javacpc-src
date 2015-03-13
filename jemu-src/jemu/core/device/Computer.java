package jemu.core.device;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import jemu.core.*;
import jemu.core.cpu.*;
import jemu.core.device.memory.*;
import jemu.ui.*;
import jemu.util.diss.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public abstract class Computer extends Device implements Runnable {

  // Entries are Name, Key, Class, Shown in list
  
  public static final ComputerDescriptor[] COMPUTERS = {
    new ComputerDescriptor("BBC-B",      "Acorn BBC Model B",        "jemu.system.bbc.BBC",           true),
    new ComputerDescriptor("CPC464",     "Amstrad CPC464",           "jemu.system.cpc.CPC",           true),
    new ComputerDescriptor("CPC664",     "Amstrad CPC664",           "jemu.system.cpc.CPC",           false),
    new ComputerDescriptor("CPC6128",    "Amstrad CPC6128",          "jemu.system.cpc.CPC",           false),
    new ComputerDescriptor("VZ200",      "Dick Smith VZ-200",        "jemu.system.vz.VZ",             false),
    new ComputerDescriptor("VZ300",      "Dick Smith VZ-300",        "jemu.system.vz.VZ",             true),
    new ComputerDescriptor("SPECTRUM48", "Sinclair ZX Spectrum 48K", "jemu.system.spectrum.Spectrum", true),
    new ComputerDescriptor("ZX80",       "Sinclair ZX80",            "jemu.system.zx.ZX",             true),
    new ComputerDescriptor("ZX81",       "Sinclair ZX81",            "jemu.system.zx.ZX",             true)
  };

  public static final String DEFAULT_COMPUTER = "SPECTRUM48";

  public static final int STOP      = 0;
  public static final int STEP      = 1;
  public static final int STEP_OVER = 2;
  public static final int RUN       = 3;

  public static final int MAX_FRAME_SKIP = 20;
  public static final int MAX_FILE_SIZE  = 1024 * 1024;  // 1024K maximum

  public Applet applet;
  protected Thread thread = new Thread(this);
  protected boolean stopped = false;
  protected int action = STOP;
  protected boolean running = false;
  protected boolean waiting = false;
  protected long startTime;
  protected long startCycles;
  protected String name;
  protected String romPath;
  protected String filePath;
  protected Vector files = null;
  protected Display display;
  protected int frameSkip = 0;
  protected int runTo = -1;
  protected int mode = STOP;

  // Listeners for stopped emulation
  protected Vector listeners = new Vector(1);

  public static Computer createComputer(Applet applet, String name) throws Exception {
    for (int index = 0; index < COMPUTERS.length; index++) {
      if (COMPUTERS[index].key.equalsIgnoreCase(name)) {
        Class cl = Util.findClass(null,COMPUTERS[index].className);
        Constructor con = cl.getConstructor(new Class[] { Applet.class, String.class });
        return (Computer)con.newInstance(new Object[] { applet, name });
      }
    }
    throw new Exception("Computer " + name + " not found");
  }

  public Computer(Applet applet, String name) {
    super("Computer: " + name);
    this.applet = applet;
    this.name = name;
//    thread.setPriority(Thread.MIN_PRIORITY);
    thread.start();
  }

  protected void setBasePath(String path) {
    romPath = "system/" + path + "/rom/";
    filePath = "system/" + path + "/file/";
  }

  public void initialise() {
    reset();
  }

  public InputStream openFile(String name) throws Exception {
    System.out.println("File: " + name);
    InputStream result;
    try {
      result = new URL(applet.getCodeBase(),name).openStream();
    } catch(Exception e) {
//      e.printStackTrace();
      result = new FileInputStream(name);
    }
    if (name.toLowerCase().endsWith(".zip")) {
      ZipInputStream str = new ZipInputStream(result);
      str.getNextEntry();
      result = str;
    }
    return result;
  }

  protected int getWord(byte[] buffer, int offs) {
    return (buffer[offs] & 0xff) | ((buffer[offs + 1] << 8) & 0xff00);
  }
  
  protected int getWordBE(byte[] buffer, int offs) {
    return (buffer[offs + 1] & 0xff) | ((buffer[offs] << 8) & 0xff00);
  }

  protected int readStream(InputStream stream, byte[] buffer, int offs, int size)
    throws Exception
  {
    return readStream(stream,buffer,offs,size,true);
  }
  
  protected int readStream(InputStream stream, byte[] buffer, int offs, int size, boolean error)
    throws Exception
  {
    while(size > 0) {
      int read = stream.read(buffer,offs,size);
      if (read == -1) {
        if (error)
          throw new Exception("Unexpected end of stream");
        else
          break;
      }
      else {
        offs += read;
        size -= read;
      }
    }
    return offs;
  }
  
  public byte[] getFile(String name) {
    return getFile(name,MAX_FILE_SIZE,true);
  }
  
  public byte[] getFile(String name, int size) {
    return getFile(name,size,false);
  }
  
  public byte[] getFile(String name, int size, boolean crop) {
    byte[] buffer = new byte[size];
    int offs = 0;
    try {
      InputStream stream = null;
      try {
        stream = openFile(name);
        while (size > 0) {
          int read = stream.read(buffer,offs,size);
          if (read == -1)
            break;
          else {
            offs += read;
            size -= read;
          }
        }
      } finally {
        if (stream != null)
          stream.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (crop && offs < buffer.length) {
      byte[] result = new byte[offs];
      System.arraycopy(buffer,0,result,0,offs);
      buffer = result;
    }
    return buffer;
  }

  public void setDisplay(Display value) {
    display = value;
    displaySet();
  }

  public Display getDisplay() {
    return display;
  }

  protected void displaySet() { }

  // For now, only supporting a single Processor
  public Disassembler getDisassembler() {
    return null;
  }

  public abstract Processor getProcessor();

  public abstract Memory getMemory();

  public abstract void processKeyEvent(KeyEvent e);

  public void loadFile(String name) throws Exception { }

  public abstract Dimension getDisplaySize(boolean large);
  
  public void setLarge(boolean value) { }
  
  public Dimension getDisplayScale(boolean large) {
    return large ? Display.SCALE_2 : Display.SCALE_1;
  }

  public void start() {
    setAction(RUN);
  }

  public void stop() {
    setAction(STOP);
  }

  public void step() {
    setAction(STEP);
  }

  public void stepOver() {
    setAction(STEP_OVER);
  }

  public synchronized void setAction(int value) {
    if (running && value != RUN) {
      action = STOP;
      //System.out.println(this + " Stopping " + getProcessor());
      getProcessor().stop();
      display.setPainted(true);
      while(running) {
        try {
          //System.out.println("stopping...");
          Thread.sleep(200);
        } catch(Exception e) {
          e.printStackTrace();
        }
      }
    }
    //System.out.println("Entering synchronized");
    synchronized(thread) {
      action = value;
      thread.notify();
    }
  }

  public void dispose() {
    stopped = true;
    //System.out.println(this + " thread stopped: " + thread);
    stop();
    //System.out.println(this + " has stopped");
    try {
      thread.join();
    } catch(Exception e) {
      e.printStackTrace();
    }
    thread = null;
    display = null;
    applet = null;
  }

  protected void emulate(int mode) {
    switch(mode) {
      case STEP:
        getProcessor().step();
        break;

      case STEP_OVER:
        getProcessor().stepOver();
        break;

      case RUN:
        if (runTo == -1)
          getProcessor().run();
        else
          getProcessor().runTo(runTo);
        break;
    }
  }

  public void addActionListener(ActionListener listener) {
    listeners.addElement(listener);
  }

  public void removeActionListener(ActionListener listener) {
    listeners.removeElement(listener);
  }

  protected void fireActionEvent() {
    ActionEvent e = new ActionEvent(this,0,null);
    for (int i = 0; i < listeners.size(); i++)
      ((ActionListener)listeners.elementAt(i)).actionPerformed(e);
  }

  public String getROMPath() {
    return romPath;
  }

  public String getFilePath() {
    return filePath;
  }

  public void reset() {
    //System.out.println(this + " Reset");
    boolean run = running;
    stop();
    getProcessor().reset();
    if (run)
      start();
  }

  public void run() {
    while(!stopped) {
      try {
        if (action == STOP) {
          synchronized(thread) {
            //System.out.println(this + " Waiting");
            thread.wait();
            //System.out.println(this + " Not Waiting");
          }
        }
        if (action != STOP) {
          try {
            //System.out.println(this + " Running");
            running = true;
            synchronized(thread) {
              mode = action;
              action = STOP;
            }
            startCycles = getProcessor().getCycles();
            startTime = System.currentTimeMillis();
            emulate(mode);
          } finally {
            running = false;
            //System.out.println(this + " Not running");
            fireActionEvent();
          }
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  public Vector getFiles() {
    if (files == null) {
      files = new Vector();
      LineNumberReader reader = null;
      try {
        reader = new LineNumberReader(new InputStreamReader(openFile(filePath +
          "Files.txt")));
        String line;
        while((line = reader.readLine()) != null) {
          int iDesc = line.indexOf('=');
          if (iDesc != -1) {
            String desc = line.substring(0,iDesc).trim();
            int iName = line.indexOf(',',iDesc + 1);
            if (iName == -1)
              iName = line.length();
            String name = line.substring(iDesc + 1,iName).trim();
            String instructions = iName < line.length() ?
              line.substring(iName + 1).trim().replace('|','\n') : "";
            files.addElement(new FileDescriptor(desc,name,instructions));
          }
        }
      } catch(Exception e) {
        System.out.println("Cannot get file list for " + this);
      } finally {
        if (reader != null)
          try {
            reader.close();
          } catch(Exception e) {
            e.printStackTrace();
          }
      }
    }
    return files;
  }

  public String getFileInfo(String fileName) {
    String result = null;
    getFiles();
    for (int i = 0; i < files.size(); i++) {
      FileDescriptor file = (FileDescriptor)files.elementAt(i);
      if (file.filename.equalsIgnoreCase(fileName)) {
        result = file.instructions;
        break;
      }
    }
    return result;
  }

  public boolean isRunning() {
    return running;
  }

  protected void syncProcessor() {
    startTime += (((getProcessor().getCycles() - startCycles) * 2000 /
      getProcessor().getCyclesPerSecond()) + 1) / 2;
    startCycles = getProcessor().getCycles();
    long time = System.currentTimeMillis();
    //System.out.print(" " + startTime);
    if (time > startTime) {
      if (frameSkip == MAX_FRAME_SKIP) {
        setFrameSkip(0);
        //System.out.println(" R: " + (time - startTime));
        startTime = time + 1;
      }
      else {
        //System.out.print(" S" + frameSkip);
        setFrameSkip(frameSkip + 1);
      }
    }
    else {
      try {
        setFrameSkip(0);
        while(System.currentTimeMillis() < startTime) ;
      } catch(Exception e) {
        e.printStackTrace();
        return;
      }
    }
  }

  public void setFrameSkip(int value) {
    frameSkip = value;
  }

  public void displayLostFocus() { }
  
  public void updateDisplay(boolean wait) { }

  public String getName() {
    return name;
  }

  public void setRunToAddress(int value) {
    runTo = value;
  }

  public void clearRunToAddress() {
    runTo = -1;
  }

  public int getMode() {
    return mode;
  }

}