/*  Copyright (C) 2003-2011 JabRef contributors.
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/
package net.sf.jabref.collab;

import java.util.Enumeration;
import java.util.TreeSet;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;

import net.sf.jabref.*;
import net.sf.jabref.undo.NamedCompound;
import net.sf.jabref.undo.UndoableFieldChange;

class EntryChange extends Change {

    private final BibtexEntry memEntry;
    private final BibtexEntry tmpEntry;
    private final BibtexEntry diskEntry;
    private final boolean isModifiedLocally;
    private final boolean modificationsAgree;


    public EntryChange(BibtexEntry memEntry, BibtexEntry tmpEntry, BibtexEntry diskEntry) {
        super();
        String key = tmpEntry.getCiteKey();
        if (key == null) {
            name = "Modified entry";
        } else {
            name = "Modified entry: '" + key + '\'';
        }
        this.memEntry = memEntry;
        this.tmpEntry = tmpEntry;
        this.diskEntry = diskEntry;

        // We know that tmpEntry is not equal to diskEntry. Check if it has been modified
        // locally as well, since last tempfile was saved.
        isModifiedLocally = !(DuplicateCheck.compareEntriesStrictly(memEntry, tmpEntry) > 1);

        // Another (unlikely?) possibility is that both disk and mem version has been modified
        // in the same way. Check for this, too.
        modificationsAgree = (DuplicateCheck.compareEntriesStrictly(memEntry, diskEntry) > 1);

        //Util.pr("Modified entry: "+memEntry.getCiteKey()+"\n Modified locally: "+isModifiedLocally
        //        +" Modifications agree: "+modificationsAgree);

        TreeSet<String> allFields = new TreeSet<String>();
        allFields.addAll(memEntry.getAllFields());
        allFields.addAll(tmpEntry.getAllFields());
        allFields.addAll(diskEntry.getAllFields());

        for (String field : allFields) {
            String mem = memEntry.getField(field), tmp = tmpEntry.getField(field), disk = diskEntry.getField(field);

            if ((tmp != null) && (disk != null)) {
                if (!tmp.equals(disk)) {
                    // Modified externally.
                    add(new FieldChange(field, memEntry, tmpEntry, mem, tmp, disk));
                }
            } else if ((tmp == null) && (disk != null) && !disk.isEmpty() || (disk == null) && (tmp != null) && !tmp.isEmpty()
                    && (mem != null) && !mem.isEmpty()) {
                // Added externally.
                add(new FieldChange(field, memEntry, tmpEntry, mem, tmp, disk));
            }

            //Util.pr("Field: "+fld.next());
        }
    }

    @Override
    public boolean makeChange(BasePanel panel, BibtexDatabase secondary, NamedCompound undoEdit) {
        boolean allAccepted = true;

        @SuppressWarnings("unchecked")
        Enumeration<Change> e = children();
        for (; e.hasMoreElements();) {
            Change c = e.nextElement();
            if (c.isAcceptable() && c.isAccepted()) {
                c.makeChange(panel, secondary, undoEdit);
            } else {
                allAccepted = false;
            }
        }

        /*panel.database().removeEntry(memEntry.getId());
        try {
          diskEntry.setId(Util.createNeutralId());
        } catch (KeyCollisionException ex) {}
        panel.database().removeEntry(memEntry.getId());*/

        return allAccepted;
    }

    @Override
    JComponent description() {
        return new JLabel(name);
    }


    static class FieldChange extends Change {

        final BibtexEntry entry;
        final BibtexEntry tmpEntry;
        final String field;
        final String inMem;
        final String onTmp;
        final String onDisk;
        final InfoPane tp = new InfoPane();
        final JScrollPane sp = new JScrollPane(tp);


        public FieldChange(String field, BibtexEntry memEntry, BibtexEntry tmpEntry, String inMem, String onTmp, String onDisk) {
            entry = memEntry;
            this.tmpEntry = tmpEntry;
            name = field;
            this.field = field;
            this.inMem = inMem;
            this.onTmp = onTmp;
            this.onDisk = onDisk;

            StringBuffer text = new StringBuffer();
            text.append("<FONT SIZE=10>");
            text.append("<H2>").append(Globals.lang("Modification of field")).append(" <I>").append(field).append("</I></H2>");

            if ((onDisk != null) && !onDisk.isEmpty()) {
                text.append("<H3>").append(Globals.lang("Value set externally")).append(":</H3>" + ' ').append(onDisk);
            } else {
                text.append("<H3>").append(Globals.lang("Value cleared externally")).append("</H3>");
            }

            if ((inMem != null) && !inMem.isEmpty()) {
                text.append("<H3>").append(Globals.lang("Current value")).append(":</H3>" + ' ').append(inMem);
            }
            if ((onTmp != null) && !onTmp.isEmpty()) {
                text.append("<H3>").append(Globals.lang("Current tmp value")).append(":</H3>" + ' ').append(onTmp);
            } else {
                // No value in memory.
                /*if ((onTmp != null) && !onTmp.equals(inMem))
                  text.append("<H2>"+Globals.lang("You have cleared this field. Original value")+":</H2>"
                              +" "+onTmp);*/
            }
            tp.setContentType("text/html");
            tp.setText(text.toString());
        }

        @Override
        public boolean makeChange(BasePanel panel, BibtexDatabase secondary, NamedCompound undoEdit) {
            //System.out.println(field+" "+onDisk);
            entry.setField(field, onDisk);
            undoEdit.addEdit(new UndoableFieldChange(entry, field, inMem, onDisk));
            tmpEntry.setField(field, onDisk);
            return true;
        }

        @Override
        JComponent description() {
            return sp;
        }

    }
}
