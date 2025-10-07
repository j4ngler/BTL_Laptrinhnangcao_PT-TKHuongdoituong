package com.example.docmgmt;

import com.example.docmgmt.config.Config;
import com.example.docmgmt.service.DocumentService;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.service.WorkflowService;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.domain.Models.Role;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "docmgmt", mixinStandardHelpOptions = true, version = "0.1",
        description = "Quản lý văn bản: thêm, liệt kê, xuất (GridFS + PostgreSQL)")
public class App implements Callable<Integer> {

    @Option(names = {"-a", "--add"}, description = "Thêm văn bản từ đường dẫn tệp")
    Path addFile;

    @Option(names = {"-t", "--title"}, description = "Tiêu đề văn bản")
    String title;

    @Option(names = {"-l", "--list"}, description = "Liệt kê văn bản")
    boolean list;

    @Option(names = {"-e", "--export"}, description = "Xuất văn bản theo id ra đường dẫn")
    String exportSpec; // format: <docId>:<outputPath>

    @Option(names = {"--add-version"}, description = "Thêm phiên bản: <docId>:<path>")
    String addVersionSpec;

    @Option(names = {"--export-version"}, description = "Xuất theo phiên bản: <docId>:<versionNo>:<outputPath>")
    String exportVersionSpec;

    @Option(names = {"-s", "--search"}, description = "Tìm kiếm theo tiêu đề chứa từ khoá")
    String search;

    @Option(names = {"--submit"}, description = "Chuyển văn bản sang SUBMITTED: <docId>:<actor>:<note>")
    String submitSpec;
    @Option(names = {"--classify"}, description = "Chuyển văn bản sang CLASSIFIED: <docId>:<actor>:<note>")
    String classifySpec;
    @Option(names = {"--approve"}, description = "Chuyển văn bản sang APPROVED: <docId>:<actor>:<note>")
    String approveSpec;
    @Option(names = {"--issue"}, description = "Chuyển văn bản sang ISSUED: <docId>:<actor>:<note>")
    String issueSpec;
    @Option(names = {"--archive"}, description = "Chuyển văn bản sang ARCHIVED: <docId>:<actor>:<note>")
    String archiveSpec;

    @Option(names = {"--add-user"}, description = "Thêm/cập nhật người dùng: <username>:<role>")
    String addUserSpec;
    @Option(names = {"--list-users"}, description = "Liệt kê người dùng")
    boolean listUsers;
    @Option(names = {"--reset-db"}, description = "Reset database (xóa và tạo lại tables)")
    boolean resetDb;

    public static void main(String[] args) {
        int exit = new CommandLine(new App()).execute(args);
        System.exit(exit);
    }

    @Override
    public Integer call() throws Exception {
        Config config = Config.fromEnv();
        try (var svc = new DocumentService(config)) {
            if (addFile != null) {
                if (title == null || title.isBlank()) {
                    System.err.println("Cần --title khi thêm văn bản");
                    return 1;
                }
                var id = svc.createDocument(title, addFile);
                System.out.println("Đã thêm văn bản: id=" + id);
                return 0;
            }
            if (addUserSpec != null || listUsers) {
                var ur = new UserRepository(config.dataSource);
                ur.migrate();
                if (addUserSpec != null) {
                    var p = addUserSpec.split(":", 2);
                    if (p.length != 2) { System.err.println("Định dạng --add-user <username>:<role>"); return 1; }
                    var role = Role.valueOf(p[1].toUpperCase());
                    long uid = ur.addUser(p[0], role);
                    System.out.println("OK user id=" + uid + ", " + p[0] + ":" + role);
                    return 0;
                }
                if (listUsers) {
                    ur.list().forEach(u -> System.out.println(u.id() + "\t" + u.username() + "\t" + u.role()));
                    return 0;
                }
            }
            if (list) {
                svc.listDocuments().forEach(d -> System.out.println(d.id() + "\t" + d.title() + "\t" + d.state() + "\t" + d.createdAt()));
                return 0;
            }
            if (search != null && !search.isBlank()) {
                svc.searchByTitle(search).forEach(d -> System.out.println(d.id() + "\t" + d.title() + "\t" + d.state() + "\t" + d.createdAt()));
                return 0;
            }
            if (exportSpec != null) {
                var parts = exportSpec.split(":", 2);
                if (parts.length != 2) {
                    System.err.println("Định dạng --export <docId>:<outputPath>");
                    return 1;
                }
                var docId = Long.parseLong(parts[0]);
                var out = Path.of(parts[1]);
                svc.exportDocument(docId, out);
                System.out.println("Đã xuất văn bản " + docId + " -> " + out);
                return 0;
            }
            if (addVersionSpec != null) {
                var parts = addVersionSpec.split(":", 2);
                if (parts.length != 2) {
                    System.err.println("Định dạng --add-version <docId>:<path>");
                    return 1;
                }
                var docId = Long.parseLong(parts[0]);
                var p = Path.of(parts[1]);
                var v = svc.addVersion(docId, title != null ? title : ("doc-" + docId), p);
                System.out.println("Đã thêm phiên bản v" + v + " cho văn bản " + docId);
                return 0;
            }
            if (exportVersionSpec != null) {
                var parts = exportVersionSpec.split(":", 3);
                if (parts.length != 3) {
                    System.err.println("Định dạng --export-version <docId>:<versionNo>:<outputPath>");
                    return 1;
                }
                var docId = Long.parseLong(parts[0]);
                var vno = Integer.parseInt(parts[1]);
                var out = Path.of(parts[2]);
                svc.exportVersion(docId, vno, out);
                System.out.println("Đã xuất văn bản " + docId + " phiên bản v" + vno + " -> " + out);
                return 0;
            }
            if (submitSpec != null || classifySpec != null || approveSpec != null || issueSpec != null || archiveSpec != null) {
                var repo = new DocumentRepository(config.dataSource);
                var ur = new UserRepository(config.dataSource);
                ur.migrate();
                var wf = new WorkflowService(repo, ur);
                if (submitSpec != null) { var p = submitSpec.split(":", 3); wf.submit(Long.parseLong(p[0]), p[1], p.length>2?p[2]:""); return 0; }
                if (classifySpec != null) { var p = classifySpec.split(":", 3); wf.classify(Long.parseLong(p[0]), p[1], p.length>2?p[2]:""); return 0; }
                if (approveSpec != null) { var p = approveSpec.split(":", 3); wf.approve(Long.parseLong(p[0]), p[1], p.length>2?p[2]:""); return 0; }
                if (issueSpec != null) { var p = issueSpec.split(":", 3); wf.issue(Long.parseLong(p[0]), p[1], p.length>2?p[2]:""); return 0; }
                if (archiveSpec != null) { var p = archiveSpec.split(":", 3); wf.archive(Long.parseLong(p[0]), p[1], p.length>2?p[2]:""); return 0; }
            }
            if (resetDb) {
                try (var c = config.dataSource.getConnection(); var st = c.createStatement()) {
                    st.executeUpdate("DROP TABLE IF EXISTS users CASCADE");
                    st.executeUpdate("DROP TABLE IF EXISTS documents CASCADE");
                    st.executeUpdate("DROP TABLE IF EXISTS document_versions CASCADE");
                    st.executeUpdate("DROP TABLE IF EXISTS audit_logs CASCADE");
                }
                var docRepo = new DocumentRepository(config.dataSource);
                var userRepo = new UserRepository(config.dataSource);
                docRepo.migrate();
                userRepo.migrate();
                System.out.println("Database đã được reset");
                return 0;
            }
            new CommandLine(this).usage(System.out);
            return 0;
        }
    }
}

