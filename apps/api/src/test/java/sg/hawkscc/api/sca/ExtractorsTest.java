package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import sg.hawkscc.api.sca.extract.CsvTableReader;
import sg.hawkscc.api.sca.extract.ExportLinkFinder;
import sg.hawkscc.api.sca.extract.HtmlTableExtractor;
import sg.hawkscc.api.sca.model.RawTable;

/** Uses small synthetic HTML pages; the real SCA markup is verified against live pages separately. */
class ExtractorsTest {

    private static final String BASE = "https://scores.example.test/SingaporeCricketAssoc/teamBatting.do?teamId=1&clubId=2";

    @Test
    void findsAnchorExportLink() {
        Document doc = Jsoup.parse("""
                <a href="viewTeam.do?teamId=1">Team</a>
                <a class="btn" href="exportTeamBatting.do?teamId=1&clubId=2&format=csv"><i class="fa fa-file"></i> CSV</a>
                """, BASE);
        assertThat(ExportLinkFinder.find(doc)).get().hasToString(
                "https://scores.example.test/SingaporeCricketAssoc/exportTeamBatting.do?teamId=1&clubId=2&format=csv");
    }

    @Test
    void findsOnclickExportUrl() {
        Document doc = Jsoup.parse("""
                <button onclick="window.location='downloadBattingCsv.do?teamId=1'">Export to CSV</button>
                """, BASE);
        assertThat(ExportLinkFinder.find(doc)).get().hasToString(
                "https://scores.example.test/SingaporeCricketAssoc/downloadBattingCsv.do?teamId=1");
    }

    @Test
    void clientSideDataTablesButtonHasNoUrl() {
        Document doc = Jsoup.parse("""
                <div class="dt-buttons"><button class="dt-button buttons-csv buttons-html5" type="button"><span>CSV</span></button></div>
                <a href="javascript:void(0)">Excel</a>
                """, BASE);
        assertThat(ExportLinkFinder.find(doc)).isEmpty();
    }

    @Test
    void extractsTheMatchingTableWithIds() {
        Document doc = Jsoup.parse("""
                <table><tr><th>Menu</th></tr><tr><td>x</td></tr></table>
                <table id="battingStats">
                  <thead><tr><th>#</th><th>Player</th><th>Mat</th><th>Runs</th><th>HS</th></tr></thead>
                  <tbody>
                    <tr><td>1</td><td><a href="viewPlayer.do?playerId=101&clubId=2">Player One</a></td><td>8</td><td>312</td><td>88*</td></tr>
                    <tr><td>2</td><td><a href="viewPlayer.do?playerId=102&clubId=2">Player Two</a></td><td>7</td><td>-</td><td>-</td></tr>
                    <tr><td colspan="5">No more records</td></tr>
                  </tbody>
                </table>
                """, BASE);
        RawTable t = HtmlTableExtractor.extract(doc, List.of("runs", "player")).orElseThrow();
        assertThat(t.headers()).containsExactly("#", "Player", "Mat", "Runs", "HS");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(0)).containsExactly("1", "Player One", "8", "312", "88*");
        assertThat(t.idsFor(0)).containsEntry("playerid", "101").containsEntry("clubid", "2");
    }

    @Test
    void noMatchingTableGivesEmpty() {
        Document doc = Jsoup.parse("<table><tr><th>Menu</th></tr></table>", BASE);
        assertThat(HtmlTableExtractor.extract(doc, List.of("runs"))).isEmpty();
    }

    @Test
    void csvReaderHandlesBomRaggedRowsAndHtmlDetection() throws Exception {
        RawTable t = CsvTableReader.read("﻿Player,Runs,HS\nA,10\n\nB,20,15*\n");
        assertThat(t.headers()).containsExactly("Player", "Runs", "HS");
        assertThat(t.rows()).containsExactly(List.of("A", "10", ""), List.of("B", "20", "15*"));
        assertThat(CsvTableReader.looksLikeCsv("<!DOCTYPE html><html>", "text/html")).isFalse();
        assertThat(CsvTableReader.looksLikeCsv("a,b\n1,2", "text/plain")).isTrue();
    }
}
