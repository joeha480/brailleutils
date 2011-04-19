package es_once_cidat;

import java.io.OutputStream;

import org.daisy.braille.embosser.EmbosserTools;
import org.daisy.braille.embosser.EmbosserWriter;
import org.daisy.braille.embosser.EmbosserFeatures;
import org.daisy.braille.embosser.ConfigurableEmbosser;
import org.daisy.braille.embosser.SimpleEmbosserProperties;
import org.daisy.braille.table.Table;
import org.daisy.braille.table.TableFilter;
import org.daisy.braille.table.TableCatalog;
import org.daisy.paper.PageFormat;

import org.daisy.braille.embosser.EmbosserFactoryException;
import org.daisy.braille.embosser.UnsupportedPaperException;

import es_once_cidat.CidatEmbosserProvider.EmbosserType;

/**
 *
 * @author Bert Frees
 */
public class ImpactoEmbosser extends CidatEmbosser {

    private final static TableFilter tableFilter;
    private final static String table6dot = CidatTableProvider.class.getCanonicalName() + ".TableType.IMPACTO_TRANSPARENT_6DOT";
    private final static String table8dot = CidatTableProvider.class.getCanonicalName() + ".TableType.IMPACTO_TRANSPARENT_8DOT";

    static {
        tableFilter = new TableFilter() {
            //jvm1.6@Override
            public boolean accept(Table object) {
                return false;
            }
        };
    }

    public ImpactoEmbosser(String name, String desc, EmbosserType identifier) {
        super(name, desc, identifier);
    }

    public TableFilter getTableFilter() {
        return tableFilter;
    }

    public EmbosserWriter newEmbosserWriter(OutputStream os) {

        if (!supportsDimensions(getPageFormat())) {
            throw new IllegalArgumentException("Unsupported paper");
        }

        boolean duplexEnabled = supportsDuplex() && false; // examine PEF file: duplex => Contract ?
        boolean eightDots = supports8dot() && false;
        int numberOfCopies = getNumberOfCopies();

        if (numberOfCopies > 32767 || numberOfCopies < 1) {
            throw new IllegalArgumentException(new EmbosserFactoryException("Invalid number of copies: " + numberOfCopies + " is not in [1, 32767]"));
        }

        PageFormat page = getPageFormat();
        int cellsInWidth = EmbosserTools.getWidth(page, getCellWidth());
        int linesInHeight = EmbosserTools.getHeight(page, getCellHeight()); // depends on cell heigth -> depends on rowgap
        
        try {

            byte[] header = getImpactoHeader();
            byte[] footer = new byte[]{0x1b,0x54};

            Table table = TableCatalog.newInstance().get(eightDots?table8dot:table6dot);

            ConfigurableEmbosser.Builder b = new ConfigurableEmbosser.Builder(os, table.newBrailleConverter())
                .breaks(new CidatLineBreaks(CidatLineBreaks.Type.IMPACTO_TRANSPARENT))
                .padNewline(ConfigurableEmbosser.Padding.NONE)
                .footer(footer)
                .embosserProperties(
                    new SimpleEmbosserProperties(cellsInWidth, linesInHeight)
                        .supportsDuplex(duplexEnabled)
                        .supportsAligning(supportsAligning())
                        .supports8dot(eightDots)
                )
                .header(header);
            return b.build();

        } catch (EmbosserFactoryException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private byte[] getImpactoHeader() throws EmbosserFactoryException {

        boolean eightDots = supports8dot() && false;    // examine PEF file: rowgap / char > 283F => Contract ?
        boolean duplex = supportsDuplex() && false;     // examine PEF file: duplex
        int pageCount = 1;                              // examine PEF file
        int copies = getNumberOfCopies();

        PageFormat page = getPageFormat();
        int pageLength = (int)Math.ceil(page.getHeight()/EmbosserTools.INCH_IN_MM);
        int charsPerLine = EmbosserTools.getWidth(page, getCellWidth());
        int linesPerPage = EmbosserTools.getHeight(page, getCellHeight()); // depends on cell heigth -> depends on rowgap
        
        if (pageLength   < 6  || pageLength   > 13) { throw new UnsupportedPaperException("Paper height = " + pageLength + " inches, must be in [6,13]"); }
        if (charsPerLine < 12 || charsPerLine > 42) { throw new UnsupportedPaperException("Characters per line = " + charsPerLine + ", must be in [12,42]"); }
        if (linesPerPage < 12 || linesPerPage > 43) { throw new UnsupportedPaperException("Lines per page = " + linesPerPage + ", must be in [12,43]"); }

        StringBuffer header = new StringBuffer();

        header.append((char)0x1b); header.append(')');                          // Transparent mode ON
        header.append((char)0x1b); header.append(eightDots?'+':'*');            // 6- or 8-dot matrix
        header.append((char)0x1b); header.append('.');
                                   header.append((char)(0x30 + pageLength));    // Page length in inches
        header.append((char)0x1b); header.append("/1");                         // Line spacing = 1/10 inch
        header.append((char)0x1b); header.append('0');
                                   header.append((char)(0x30 + charsPerLine));  // Characters per line
        header.append((char)0x1b); header.append('1');
                                   header.append((char)(0x30 + linesPerPage));  // Lines per page
        header.append((char)0x1b); header.append('3');                          // Cut off words
        header.append((char)0x1b); header.append(duplex?'Q':'P');               // Front-side or double-sided embossing
        header.append((char)0x1b); header.append("EP");
                                   header.append(String.valueOf(pageCount));
                                   header.append('\n');                         // Number of last page to emboss
        header.append((char)0x1b); header.append("GU0\n");                      // Gutter (binding margin) = 0
        header.append((char)0x1b); header.append("IN0\n");                      // Indent first line of paragraph = 0
        header.append((char)0x1b); header.append("MB0\n");                      // Bottom margin in tenths of an inch = 0
        header.append((char)0x1b); header.append("ML0\n");                      // Left margin in characters = 0
        header.append((char)0x1b); header.append("MR0\n");                      // Right margin in characters = 0
        header.append((char)0x1b); header.append("MT0\n");                      // Top margin in tenths of an inch = 0
        header.append((char)0x1b); header.append("NC1");
                                   header.append(String.valueOf(copies));
                                   header.append('\n');                         // Number of copies
        header.append((char)0x1b); header.append("SC1\n");                      // Collate copies (one full copy at a time)
        header.append((char)0x1b); header.append("PM0\n");                      // Embossing mode = text embossing mode
        header.append((char)0x1b); header.append("PN0\n");                      // Number pages = no
        header.append((char)0x1b); header.append("PI0\n");                      // Parameter influence = only present job
        header.append((char)0x1b); header.append("SP1\n");                      // Number of first page to emboss = 1

        return header.toString().getBytes();
    }

    protected int getNumberOfCopies() {

        int numberOfCopies = 1;
        Object value = getFeature(EmbosserFeatures.NUMBER_OF_COPIES);
        if (value != null) {
            try {
                numberOfCopies = (Integer)value;
            } catch (ClassCastException e) {
            }
        }
        return numberOfCopies;
    }
}