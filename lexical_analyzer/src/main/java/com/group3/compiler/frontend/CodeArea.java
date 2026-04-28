    package com.group3.compiler.frontend;

    import java.awt.datatransfer.DataFlavor;
    import java.awt.datatransfer.UnsupportedFlavorException;
    import java.awt.dnd.DnDConstants;
    import java.awt.dnd.DropTarget;
    import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import javax.swing.JTextArea;

    public class CodeArea extends JTextArea {
        public CodeArea() {
            setLineWrap(true);
            enableDragAndDrop();
        }

        private void enableDragAndDrop() {
            new DropTarget(this, new DropTargetListener() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> droppedFiles = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                        
                        // Read the first file dropped
                        File file = droppedFiles.get(0);
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        read(reader, null);
                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                    }
                }
                // Other methods (dragEnter, dragExit, etc.) can be left empty
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {}
                @Override
                public void dragOver(DropTargetDragEvent dtde) {}
                @Override
                public void dropActionChanged(DropTargetDragEvent dtde) {}
                @Override
                public void dragExit(DropTargetEvent dte) {}
            });
        }
    }