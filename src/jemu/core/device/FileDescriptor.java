/*
 * FileDescriptor.java
 *
 * Created on 18 January 2007, 12:43
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package jemu.core.device;

/**
 *
 * @author Richard
 */
public class FileDescriptor {
  
  public String description, filename, instructions;
  
  /** Creates a new instance of FileDescriptor */
  public FileDescriptor(String description, String filename, String instructions) {
    this.description = description;
    this.filename = filename;
    this.instructions = instructions;
  }
  
  public String toString() {
    return description;
  }
  
}
