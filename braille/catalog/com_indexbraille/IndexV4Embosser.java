package com_indexbraille;

import java.io.OutputStream;

import org.daisy.braille.embosser.ConfigurableEmbosser;
import org.daisy.braille.embosser.EmbosserFactoryException;
import org.daisy.braille.embosser.EmbosserTools;
import org.daisy.braille.embosser.EmbosserWriter;
import org.daisy.braille.embosser.EmbosserWriterProperties;
import org.daisy.braille.embosser.SimpleEmbosserProperties;
import org.daisy.braille.embosser.StandardLineBreaks;
import org.daisy.braille.embosser.UnsupportedPaperException;
import org.daisy.braille.table.Table;
import org.daisy.braille.table.TableCatalog;
import org.daisy.braille.table.TableFilter;
import org.daisy.paper.Dimensions;
import org.daisy.paper.PageFormat;
import org.daisy.paper.PrintPage;

import com_indexbraille.IndexEmbosserProvider.EmbosserType;

public class IndexV4Embosser extends IndexEmbosser {

    private final static TableFilter tableFilter;
    private final static String table6dot = "org.daisy.braille.table.DefaultTableProvider.TableType.EN_US";
  //private final static String table8dot = "com_indexbraille.IndexTableProvider.TableType.INDEX_TRANSPARENT_8DOT";

    static {
        tableFilter = new TableFilter() {
            //jvm1.6@Override
            public boolean accept(Table object) {
                if (object == null) { return false; }
                String tableID = object.getIdentifier();
                if (tableID.equals(table6dot)) { return true; }
              //if (tableID.equals(table8dot)) { return true; }
                return false;
            }
        };
    }

    public IndexV4Embosser(String name, String desc,  EmbosserType identifier) {

        super(name, desc, identifier);

        setTable = TableCatalog.newInstance().get(table6dot);

        switch (type) {
            case INDEX_BASIC_D_V4:
            case INDEX_EVEREST_D_V4:
                duplexEnabled = true;
                break;
            default:
                throw new IllegalArgumentException("Unsupported embosser type");
        }

        maxNumberOfCopies = 10000;
        maxCellsInWidth = 42;
        maxMarginInner = 10;
        maxMarginOuter = 10;
        maxMarginTop = 10;
    }

    public TableFilter getTableFilter() {
        return tableFilter;
    }

    @Override
    public boolean supportsDimensions(Dimensions dim) {

        if (type==EmbosserType.INDEX_BASIC_D_V4) {
            double w = dim.getWidth();
            double h = dim.getHeight();
            return super.supportsDimensions(dim) && (w==210 && (h==10*EmbosserTools.INCH_IN_MM ||
                                                                h==11*EmbosserTools.INCH_IN_MM ||
                                                                h==12*EmbosserTools.INCH_IN_MM)
                                                  || w==240 &&  h==12*EmbosserTools.INCH_IN_MM
                                                  || w==280 &&  h==12*EmbosserTools.INCH_IN_MM);
        } else {
            return super.supportsDimensions(dim);
        }
    }

    public boolean supportsMagazineLayout() {

        switch (type) {
            case INDEX_EVEREST_D_V4:
                return true;
            default:
                return false;
        }
    }

    public boolean supportsZFolding() {

        switch (type) {
            case INDEX_BASIC_D_V4:
                return true;
            default:
                return false;
        }
    }

    public EmbosserWriter newEmbosserWriter(OutputStream os) {

        PageFormat page = getPageFormat();
        if (!supportsPageFormat(page)) {
            throw new IllegalArgumentException(new UnsupportedPaperException("Unsupported paper"));
        }

       // getPrintableArea(page);
        int cellsInWidth = (int)Math.floor(printablePageWidth/getCellWidth());

        if (cellsInWidth > maxCellsInWidth) {
            throw new IllegalArgumentException(new UnsupportedPaperException("Unsupported paper"));
        }
        if (numberOfCopies > maxNumberOfCopies || numberOfCopies < 1) {
            throw new IllegalArgumentException(new EmbosserFactoryException("Invalid number of copies: " + numberOfCopies + " is not in [1, 10000]"));
        }

        byte[] header = getIndexV3Header(eightDotsEnabled, duplexEnabled);
        byte[] footer = new byte[0];

        EmbosserWriterProperties props =
            new SimpleEmbosserProperties(getMaxWidth(page), getMaxHeight(page))
                .supports8dot(eightDotsEnabled)
                .supportsDuplex(duplexEnabled)
                .supportsAligning(supportsAligning());

        if (eightDotsEnabled) {
            return new IndexTransparentEmbosserWriter(os,
                                                      setTable.newBrailleConverter(),
                                                      header,
                                                      footer,
                                                      props);
        } else {
            return new ConfigurableEmbosser.Builder(os, setTable.newBrailleConverter())
                            .breaks(new StandardLineBreaks(StandardLineBreaks.Type.DOS))
                            .padNewline(ConfigurableEmbosser.Padding.NONE)
                            .footer(footer)
                            .embosserProperties(props)
                            .header(header)
                            .build();
        }
    }

    private byte[] getIndexV3Header(boolean eightDots,
                                    boolean duplex) {

        PrintPage page = getPrintPage(getPageFormat());
        double paperWidth = page.getWidth();
        double paperLenght = page.getHeight();
        int cellsInWidth = (int)Math.floor(printablePageWidth/getCellWidth());

        byte[] xx;
        byte y;
        double iPart;
        double fPart;

        StringBuffer header = new StringBuffer();

        header.append((char)0x1b);
        header.append("D");                                         // Activate temporary formatting properties of a document
        header.append("BT0");                                       // Default braille table
        header.append(",TD0");                                      // Text dot distance = 2.5 mm
        header.append(",LS50");                                     // Line spacing = 5 mm
        header.append(",DP");
        if (saddleStitchEnabled)       { header.append('4'); } else
        if (zFoldingEnabled && duplex) { header.append('3'); } else
        if (zFoldingEnabled)           { header.append('5'); } else
        if (duplex)                    { header.append('2'); } else
                                       { header.append('1'); }      // Page mode
        if (numberOfCopies > 1) {
            header.append(",MC");
            header.append(String.valueOf(numberOfCopies));          // Multiple copies
        }
        //header.append(",MI1");                                    // Multiple impact = 1
        header.append(",PN0");                                      // No page number
        switch (type) {
            case INDEX_BASIC_D_V4:
                iPart = Math.floor(paperLenght/EmbosserTools.INCH_IN_MM);
                fPart = (paperLenght/EmbosserTools.INCH_IN_MM - iPart);
                                     xx = EmbosserTools.toBytes((int)iPart, 2);
                if (fPart > 0.75)  { xx = EmbosserTools.toBytes((int)(iPart + 1), 2);
                                     y = '0'; } else
                if (fPart > 2d/3d) { y = '5'; } else
                if (fPart > 0.5)   { y = '4'; } else
                if (fPart > 1d/3d) { y = '3'; } else
                if (fPart > 0.25)  { y = '2'; } else
                if (fPart > 0)     { y = '1'; } else
                                   { y = '0'; }
                header.append(",PL");
                header.append((char)xx[0]);
                header.append((char)xx[1]);
                header.append((char)y);                             // Paper length
                break;
            case INDEX_EVEREST_D_V4:
                header.append(",PL");
                header.append(String.valueOf(
                        (int)Math.ceil(paperLenght)));              // Paper length
                header.append(",PW");
                header.append(String.valueOf(
                        (int)Math.ceil(paperWidth)));               // Paper width
                break;
            default:
        }
        header.append(",CH");
        header.append(String.valueOf(cellsInWidth));                // Characters per line
        header.append(",IM");
        header.append(String.valueOf(marginInner));                 // Inner margin
        header.append(",OM");
        header.append(String.valueOf(marginOuter));                 // Outer margin
        header.append(",TM");
        header.append(String.valueOf(marginTop));                   // Top margin
        header.append(",BM");
        header.append(String.valueOf(marginBottom));                // Bottom margin
        header.append(";");

        return header.toString().getBytes();

    }
}