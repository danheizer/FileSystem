import java.util.Vector;
public class FileTable 
{
    /*
     * The file system maintains the file (structure) table shared among all user threads. 
     * When a user thread opens a file, it follows the sequence listed below:
     * 1. The user thread allocates a new entry of the user file descriptor table in its TCB. 
     *          This entry number itself becomes a file descriptor number. The entry maintains a 
     *          reference to an file (structure) table entry which will be obtained from the file 
     *          system in the following sequence.
     *          
     * 2. The user thread then requests the file system to allocate a new entry of the system-maintained file (structure)table.
     *      This entry includes the seek pointer of this file, a reference to the inode corresponding to the file, 
     *      the inode number, the count to maintain #threads sharing this file (structure) table, and the access mode. 
     *      The seek pointer is set to the front or the tail of this file depending on the file access mode.

     * 3. The file system locates the corresponding inode and records it in this file (structure) table entry.
     * 
     * 4. The user thread finally registers a reference to this file (structure) table entry in its file descriptor
     *      table entry of the TCB.
     */

    private Vector<FileTableEntry> table;         // the actual entity of this file table
    private Directory dir;        // the root directory 

    public FileTable( Directory directory ) 
    { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    
    public synchronized FileTableEntry falloc(String fileName, String mode) {
        Inode myNode = null;
        short blockID;
        while(true) 
        {
            if(fileName.equals("/")) {
                blockID = 0;
            } else {
                blockID = dir.namei(fileName); 
            }

            if(blockID >= 0) { //Not superblock
                myNode = new Inode(blockID);
                break;
            }

            if(mode.compareTo("r") == 0) {
                return null;
            }
            blockID = dir.ialloc(fileName);
            myNode = new Inode();
            break;
        }

        myNode.count++;
        myNode.toDisk(blockID);
        FileTableEntry toAdd = new FileTableEntry(myNode, blockID, mode);
        table.addElement(toAdd);
        return toAdd;
    }

    public synchronized boolean ffree( FileTableEntry e )
    {
        // receive a file table entry reference
        // save the corresponding inode to the disk
        // free this file table entry.
        // return true if this file table entry found in my table

        if(table.removeElement(e))
        {
            e.inode.count--;
            e.inode.toDisk(e.iNumber);
            e = null;
            return true;

        } else return false;
    }

    public synchronized boolean fempty( )
    {
        return table.isEmpty( );  // return if table is empty 
    }                            // should be called before starting a format
}