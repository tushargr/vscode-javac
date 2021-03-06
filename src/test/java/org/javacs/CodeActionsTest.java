package org.javacs;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.*;
import org.junit.Before;
import org.junit.Test;

public class CodeActionsTest {

    private static List<Diagnostic> diagnostics = new ArrayList<>();

    @Before
    public void before() {
        diagnostics.clear();
    }

    private static final JavaLanguageServer server =
            LanguageServerFixture.getJavaLanguageServer(
                    LanguageServerFixture.DEFAULT_WORKSPACE_ROOT, diagnostics::add);

    @Test
    public void addImport() {
        List<String> titles =
                commands("/org/javacs/example/MissingImport.java", 5, 14)
                        .stream()
                        .map(c -> c.getTitle())
                        .collect(Collectors.toList());

        assertThat(titles, hasItem("Import java.util.ArrayList"));
    }

    @Test
    public void missingImport() {
        String message =
                "cannot find symbol\n"
                        + "  symbol:   class ArrayList\n"
                        + "  location: class org.javacs.MissingImport";

        assertThat(
                CodeActions.cannotFindSymbolClassName(message), equalTo(Optional.of("ArrayList")));
    }

    private List<? extends Command> commands(String file, int row, int column) {
        URI uri = FindResource.uri(file);
        TextDocumentIdentifier document = new TextDocumentIdentifier(uri.toString());

        try {
            InputStream in = Files.newInputStream(new File(uri).toPath());
            String content =
                    new BufferedReader(new InputStreamReader(in))
                            .lines()
                            .collect(Collectors.joining("\n"));
            TextDocumentItem open = new TextDocumentItem();

            open.setText(content);
            open.setUri(uri.toString());
            open.setLanguageId("java");

            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(open, content));
            server.getTextDocumentService()
                    .didSave(new DidSaveTextDocumentParams(document, content));

            return diagnostics
                    .stream()
                    .filter(diagnostic -> includes(diagnostic.getRange(), row - 1, column - 1))
                    .flatMap(diagnostic -> codeActionsAt(document, diagnostic))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean includes(Range range, int line, int character) {
        boolean startCondition =
                range.getStart().getLine() < line
                        || (range.getStart().getLine() == line
                                && range.getStart().getCharacter() <= character);
        boolean endCondition =
                line < range.getEnd().getLine()
                        || (line == range.getEnd().getLine()
                                && character <= range.getEnd().getCharacter());

        return startCondition && endCondition;
    }

    private Stream<? extends Command> codeActionsAt(
            TextDocumentIdentifier documentId, Diagnostic diagnostic) {
        CodeActionParams params =
                new CodeActionParams(
                        documentId, diagnostic.getRange(), new CodeActionContext(diagnostics));

        return server.getTextDocumentService().codeAction(params).join().stream();
    }
}
