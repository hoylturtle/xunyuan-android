using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Windows.Forms;

// Single portable EXE: checksum-verified bundled 7zr extracts the solid archive.
// The standalone decoder is public domain; no installation or elevation.
internal static class PortableLauncher {
    private static void ExtractResource(Assembly assembly, string name, string target, string expected) {
        using (Stream stream = assembly.GetManifestResourceStream(name)) {
            if (stream == null) throw new IOException("Bundled component missing: " + name);
            using (var hash = SHA256.Create()) {
                string actual = BitConverter.ToString(hash.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
                if (actual != expected) throw new IOException("Bundled component checksum mismatch.");
            }
        }
        using (Stream stream = assembly.GetManifestResourceStream(name))
        using (var output = new FileStream(target, FileMode.CreateNew)) { stream.CopyTo(output); }
    }
    [STAThread]
    private static int Main(string[] args) {
        string folder = null;
        try {
            if (args.Length > 1 || (args.Length == 1 && args[0] != "--self-test" && args[0] != "--smoke-test"))
                throw new ArgumentException("Unsupported launch option.");
            folder = Path.Combine(Path.GetTempPath(), "Xunyuan-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(folder);
            var assembly = Assembly.GetExecutingAssembly();
            string archive = Path.Combine(folder, "payload.7z");
            string decoder = Path.Combine(folder, "7zr.exe");
            ExtractResource(assembly, "payload.7z", archive, "PAYLOAD_SHA256");
            ExtractResource(assembly, "7zr.exe", decoder, "DECODER_SHA256");
            string bundle = Path.Combine(folder, "bundle");
            var unpack = new ProcessStartInfo(decoder, "x -y -bd -o\"" + bundle + "\" \"" + archive + "\"");
            unpack.UseShellExecute = false; unpack.CreateNoWindow = true;
            using (var process = Process.Start(unpack)) {
                if (!process.WaitForExit(60000)) { process.Kill(); process.WaitForExit(); throw new IOException("Runtime extraction timed out."); }
                if (process.ExitCode != 0) throw new IOException("Runtime extraction failed (" + process.ExitCode + ").");
            }
            File.Delete(archive);
            string java = Path.Combine(bundle, "Xunyuan", "runtime", "bin", "javaw.exe");
            string jar = Path.Combine(bundle, "Xunyuan", "app", "Xunyuan.jar");
            var start = new ProcessStartInfo(java, "-Dfile.encoding=UTF-8 -jar \"" + jar + "\"" + (args.Length == 1 ? " " + args[0] : ""));
            start.UseShellExecute = false; start.CreateNoWindow = true;
            using (var process = Process.Start(start)) { process.WaitForExit(); return process.ExitCode; }
        } catch (Exception ex) {
            if (args.Length == 0) MessageBox.Show("寻源启动失败：\n" + ex.Message, "寻源", MessageBoxButtons.OK, MessageBoxIcon.Error);
            else Console.Error.WriteLine(ex);
            return 1;
        } finally {
            if (folder != null) { try { Directory.Delete(folder, true); } catch { /* OS may still hold a runtime file. */ } }
        }
    }
}
