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
package net.sf.jabref.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;

import javax.swing.*;
import javax.swing.table.TableColumnModel;

import ca.odell.glazedlists.gui.AbstractTableComparatorChooser;
import net.sf.jabref.*;
import net.sf.jabref.external.ExternalFileMenuItem;
import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.event.ListEvent;
import ca.odell.glazedlists.event.ListEventListener;
import ca.odell.glazedlists.gui.AdvancedTableFormat;
import ca.odell.glazedlists.swing.EventSelectionModel;
import ca.odell.glazedlists.swing.EventTableModel;
import ca.odell.glazedlists.swing.TableComparatorChooser;

import com.jgoodies.uif_lite.component.UIFSplitPane;

/**
 * Dialog to display search results, potentially from more than one BasePanel, with
 * possibility to preview and to locate each entry in the main window.
 *
 * TODO: should be possible to save or export the list.
 */
public class SearchResultsDialog {

    private final JabRefFrame frame;

    private JDialog diag;
    private final String[] fields = new String[] {
            "author", "title", "year", "journal"
    };
    private final int FILE_COL = 0;
    private final int URL_COL = 1;
    private final int PAD = 2;
    private final JLabel fileLabel = new JLabel(GUIGlobals.getImage("psSmall"));
    private final JLabel urlLabel = new JLabel(GUIGlobals.getImage("wwwSmall"));

    private final Rectangle toRect = new Rectangle(0, 0, 1, 1);

    private EventTableModel<BibtexEntry> model;
    private final EventList<BibtexEntry> entries = new BasicEventList<BibtexEntry>();
    private SortedList<BibtexEntry> sortedEntries;
    private final HashMap<BibtexEntry, BasePanel> entryHome = new HashMap<BibtexEntry, BasePanel>();

    private JTable entryTable;
    private final UIFSplitPane contentPane = new UIFSplitPane(JSplitPane.VERTICAL_SPLIT);
    private PreviewPanel preview;


    public SearchResultsDialog(JabRefFrame frame, String title) {

        this.frame = frame;

        init(title);
    }

    private void init(String title) {
        diag = new JDialog(frame, title, false);

        int activePreview = Globals.prefs.getInt("activePreview");
        preview = new PreviewPanel(null, new MetaData(),
                activePreview == 0 ? Globals.prefs.get("preview0") : Globals.prefs.get("preview1"));

        sortedEntries = new SortedList<BibtexEntry>(entries, new EntryComparator(false, true, "author"));
        model = new EventTableModel<BibtexEntry>(sortedEntries,
                new EntryTableFormat());
        entryTable = new JTable(model);
        GeneralRenderer renderer = new GeneralRenderer(Color.white);
        entryTable.setDefaultRenderer(JLabel.class, renderer);
        entryTable.setDefaultRenderer(String.class, renderer);
        setWidths();
        TableComparatorChooser<BibtexEntry> tableSorter =
                TableComparatorChooser.install(entryTable, sortedEntries,
                        AbstractTableComparatorChooser.MULTIPLE_COLUMN_KEYBOARD);
        setupComparatorChooser(tableSorter);
        JScrollPane sp = new JScrollPane(entryTable);

        final EventSelectionModel<BibtexEntry> selectionModel = new EventSelectionModel<BibtexEntry>(sortedEntries);
        entryTable.setSelectionModel(selectionModel);
        selectionModel.getSelected().addListEventListener(new EntrySelectionListener());
        entryTable.addMouseListener(new TableClickListener());

        contentPane.setTopComponent(sp);
        contentPane.setBottomComponent(preview);

        // Key bindings:
        AbstractAction closeAction = new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                diag.dispose();
            }
        };
        ActionMap am = contentPane.getActionMap();
        InputMap im = contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.put(Globals.prefs.getKey("Close dialog"), "close");
        am.put("close", closeAction);

        entryTable.getActionMap().put("copy", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectionModel.getSelected().size() > 0) {
                    BibtexEntry[] bes = selectionModel.getSelected().toArray
                            (new BibtexEntry[selectionModel.getSelected().size()]);
                    TransferableBibtexEntry trbe
                    = new TransferableBibtexEntry(bes);
                    // ! look at ClipBoardManager
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(trbe, frame.basePanel());
                    frame.output(Globals.lang("Copied") + ' ' + (bes.length > 1 ? bes.length + " "
                            + Globals.lang("entries")
                            : "1 " + Globals.lang("entry") + '.'));
                }
            }
        });

        diag.addWindowListener(new WindowAdapter() {

            @Override
            public void windowOpened(WindowEvent e) {
                contentPane.setDividerLocation(0.5f);
            }

            @Override
            public void windowClosing(WindowEvent event) {
                Globals.prefs.putInt("searchDialogWidth", diag.getSize().width);
                Globals.prefs.putInt("searchDialogHeight", diag.getSize().height);
            }
        });

        diag.getContentPane().add(contentPane, BorderLayout.CENTER);
        // Remember and default to last size:
        diag.setSize(new Dimension(Globals.prefs.getInt("searchDialogWidth"), Globals.prefs
                .getInt("searchDialogHeight")));
        diag.setLocationRelativeTo(frame);
    }

    /**
     * Control the visibility of the dialog.
     * @param visible true to show dialog, false to hide.
     */
    public void setVisible(boolean visible) {
        diag.setVisible(visible);
    }

    public void selectFirstEntry() {
        if (entryTable.getRowCount() > 0) {
            entryTable.setRowSelectionInterval(0, 0);
        } else {
            contentPane.setDividerLocation(1.0f);
        }
    }

    /**
     * Remove all entries from the table.
     */
    public synchronized void clear() {
        entries.clear();
        entryHome.clear();
    }

    /**
     * Set up the comparators for each column, so the user can modify sort order
     * by clicking the column labels.
     * @param comparatorChooser The comparator chooser controlling the sort order.
     */
    @SuppressWarnings("unchecked")
    private void setupComparatorChooser(TableComparatorChooser<BibtexEntry> comparatorChooser) {
        // First column:
        java.util.List<Comparator> comparators = comparatorChooser
                .getComparatorsForColumn(0);
        comparators.clear();

        comparators = comparatorChooser.getComparatorsForColumn(1);
        comparators.clear();

        // Icon columns:
        for (int i = 2; i < PAD; i++) {
            comparators = comparatorChooser.getComparatorsForColumn(i);
            comparators.clear();
            if (i == FILE_COL) {
                comparators.add(new IconComparator(new String[] {GUIGlobals.FILE_FIELD}));
            } else if (i == URL_COL) {
                comparators.add(new IconComparator(new String[] {"url"}));
            }

        }
        // Remaining columns:
        for (int i = PAD; i < (PAD + fields.length); i++) {
            comparators = comparatorChooser.getComparatorsForColumn(i);
            comparators.clear();
            comparators.add(new FieldComparator(fields[i - PAD]));
        }

        sortedEntries.getReadWriteLock().writeLock().lock();
        comparatorChooser.appendComparator(PAD, 0, false);
        sortedEntries.getReadWriteLock().writeLock().unlock();

    }

    /**
     * Set column widths according to which field is shown, and lock icon columns
     * to a suitable width.
     */
    private void setWidths() {
        TableColumnModel cm = entryTable.getColumnModel();
        for (int i = 0; i < PAD; i++) {
            // Check if the Column is a RankingColumn
            // If this is the case, set a certain Column-width,
            // because the RankingIconColumn needs some more width
            if (frame.basePanel().tableFormat.isRankingColumn(i)) {
                // Lock the width of ranking icon column.
                cm.getColumn(i).setPreferredWidth(GUIGlobals.WIDTH_ICON_COL_RANKING);
                cm.getColumn(i).setMinWidth(GUIGlobals.WIDTH_ICON_COL_RANKING);
                cm.getColumn(i).setMaxWidth(GUIGlobals.WIDTH_ICON_COL_RANKING);
            } else {
                // Lock the width of icon columns.
                cm.getColumn(i).setPreferredWidth(GUIGlobals.WIDTH_ICON_COL);
                cm.getColumn(i).setMinWidth(GUIGlobals.WIDTH_ICON_COL);
                cm.getColumn(i).setMaxWidth(GUIGlobals.WIDTH_ICON_COL);
            }
        }

        for (int i = 0; i < fields.length; i++) {
            int width = BibtexFields.getFieldLength(fields[i]);
            cm.getColumn(i + PAD).setPreferredWidth(width);
        }
    }

    /**
     * Add a list of entries to the table.
     * @param newEntries The list of entries.
     * @param panel A reference to the BasePanel where the entries belong.
     */
    public synchronized void addEntries(java.util.List<BibtexEntry> newEntries, BasePanel panel) {
        for (BibtexEntry entry : newEntries) {
            entries.add(entry);
            entryHome.put(entry, panel);
        }
    }

    /**
     * Add a single entry to the table.
     * @param entry The entry to add.
     * @param panel A reference to the BasePanel where the entry belongs.
     */
    public synchronized void addEntry(BibtexEntry entry, BasePanel panel) {
        entries.add(entry);
        entryHome.put(entry, panel);
    }


    /**
     * Mouse listener for the entry table. Processes icon clicks to open external
     * files or urls, as well as the opening of the context menu.
     */
    class TableClickListener extends MouseAdapter {

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                processPopupTrigger(e);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                processPopupTrigger(e);
                return;
            }

            // First find the row on which the user has clicked.
            final int row = entryTable.rowAtPoint(e.getPoint());

            // A double click on an entry should highlight the entry in its BasePanel:
            if (e.getClickCount() == 2) {
                // Get the selected entry:
                BibtexEntry toShow = model.getElementAt(row);
                // Look up which BasePanel it belongs to:
                BasePanel p = entryHome.get(toShow);
                // Show the correct tab in the main window:
                frame.showBasePanel(p);
                // Highlight the entry:
                p.highlightEntry(toShow);
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.isPopupTrigger()) {
                processPopupTrigger(e);
                return;
            }
            //if (e.)
            final int col = entryTable.columnAtPoint(e.getPoint()), row = entryTable.rowAtPoint(e.getPoint());
            if (col < PAD) {
                BibtexEntry entry = sortedEntries.get(row);
                BasePanel p = entryHome.get(entry);
                switch (col) {
                case FILE_COL:
                    Object o = entry.getField(GUIGlobals.FILE_FIELD);
                    if (o != null) {
                        FileListTableModel tableModel = new FileListTableModel();
                        tableModel.setContent((String) o);
                        if (tableModel.getRowCount() == 0) {
                            return;
                        }
                        FileListEntry fl = tableModel.getEntry(0);
                        (new ExternalFileMenuItem(frame, entry, "", fl.getLink(), null,
                                p.metaData(), fl.getType())).actionPerformed(null);
                    }
                    break;
                case URL_COL:
                    Object link = entry.getField("url");
                    try {
                        if (link != null) {
                            Util.openExternalViewer(p.metaData(), (String) link, "url");
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    break;

                }
            }
        }

        /**
         * If the user has signalled the opening of a context menu, the event
         * gets redirected to this method. Here we open a file link menu if the
         * user is pointing at a file link icon. Otherwise a general context
         * menu should be shown.
         * @param e The triggering mouse event.
         */
        public void processPopupTrigger(MouseEvent e) {
            BibtexEntry entry = sortedEntries.get(entryTable.rowAtPoint(e.getPoint()));
            BasePanel p = entryHome.get(entry);
            int col = entryTable.columnAtPoint(e.getPoint());
            JPopupMenu menu = new JPopupMenu();
            int count = 0;

            if (col == FILE_COL) {
                // We use a FileListTableModel to parse the field content:
                Object o = entry.getField(GUIGlobals.FILE_FIELD);
                FileListTableModel fileList = new FileListTableModel();
                fileList.setContent((String) o);
                // If there are one or more links, open the first one:
                for (int i = 0; i < fileList.getRowCount(); i++) {
                    FileListEntry flEntry = fileList.getEntry(i);
                    String description = flEntry.getDescription();
                    if ((description == null) || (description.trim().length() == 0)) {
                        description = flEntry.getLink();
                    }
                    menu.add(new ExternalFileMenuItem(p.frame(), entry, description,
                            flEntry.getLink(), flEntry.getType().getIcon(), p.metaData(),
                            flEntry.getType()));
                    count++;
                }

            }

            if (count > 0) {
                menu.show(entryTable, e.getX(), e.getY());
            }
        }
    }

    /**
     * The listener for the Glazed list monitoring the current selection.
     * When selection changes, we need to update the preview panel.
     */
    private class EntrySelectionListener implements ListEventListener<BibtexEntry> {

        @Override
        public void listChanged(ListEvent<BibtexEntry> listEvent) {
            if (listEvent.getSourceList().size() == 1) {
                BibtexEntry entry = listEvent.getSourceList().get(0);
                // Find out which BasePanel the selected entry belongs to:
                BasePanel p = entryHome.get(entry);
                // Update the preview's metadata reference:
                preview.setMetaData(p.metaData());
                // Update the preview's entry:
                preview.setEntry(entry);
                contentPane.setDividerLocation(0.5f);
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        preview.scrollRectToVisible(toRect);
                    }
                });
            }
        }
    }

    /**
     * TableFormat for the table shown in the dialog. Handles the display of entry
     * fields and icons for linked files and urls.
     */
    private class EntryTableFormat implements AdvancedTableFormat<BibtexEntry> {

        @Override
        public int getColumnCount() {
            return PAD + fields.length;
        }

        @Override
        public String getColumnName(int column) {
            if (column >= PAD) {
                return Util.nCase(fields[column - PAD]);
            } else {
                return "";
            }
        }

        @Override
        public Object getColumnValue(BibtexEntry entry, int column) {
            if (column < PAD) {
                Object o;
                switch (column) {
                case FILE_COL:
                    o = entry.getField(GUIGlobals.FILE_FIELD);
                    if (o != null) {
                        FileListTableModel model = new FileListTableModel();
                        model.setContent((String) o);
                        fileLabel.setToolTipText(model.getToolTipHTMLRepresentation());
                        if (model.getRowCount() > 0) {
                            fileLabel.setIcon(model.getEntry(0).getType().getIcon());
                        }
                        return fileLabel;
                    } else {
                        return null;
                    }
                case URL_COL:
                    o = entry.getField("url");
                    if (o != null) {
                        urlLabel.setToolTipText((String) o);
                        return urlLabel;
                    } else {
                        return null;
                    }
                default:
                    return null;
                }
            }
            else {
                String field = fields[column - PAD];
                if (field.equals("author") || field.equals("editor")) {
                    // For name fields, tap into a MainTableFormat instance and use
                    // the same name formatting as is used in the entry table:
                    if (frame.basePanel() != null) {
                        return frame.basePanel().tableFormat.formatName
                                (entry.getField(field));
                    }
                }
                return entry.getField(field);
            }
        }

        @Override
        public Class<?> getColumnClass(int i) {
            if (i < PAD) {
                return JLabel.class;
            } else {
                return String.class;
            }
        }

        @Override
        public Comparator<?> getColumnComparator(int i) {
            return null;
        }
    }
}
