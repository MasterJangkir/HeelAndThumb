import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

const projectRoot = path.resolve('.');
const toolsDir = path.resolve('..', 'tools');
const sdkRoot = path.join(toolsDir, 'android-sdk');
const jdkRoot = path.join(toolsDir, 'jdk-17');

const aapt2 = path.join(sdkRoot, 'build-tools', '34.0.0', 'aapt2.exe');
const d8 = path.join(sdkRoot, 'build-tools', '34.0.0', 'd8.bat');
const zipalign = path.join(sdkRoot, 'build-tools', '34.0.0', 'zipalign.exe');
const apksigner = path.join(sdkRoot, 'build-tools', '34.0.0', 'apksigner.bat');
const androidJar = path.join(sdkRoot, 'platforms', 'android-34', 'android.jar');
const javac = path.join(jdkRoot, 'bin', 'javac.exe');
const jar = path.join(jdkRoot, 'bin', 'jar.exe');
const keytool = path.join(jdkRoot, 'bin', 'keytool.exe');

const buildDir = path.join(projectRoot, 'build');
const compiledResDir = path.join(buildDir, 'compiled_res');
const genDir = path.join(buildDir, 'gen');
const classesDir = path.join(buildDir, 'classes');
const unsignedApk = path.join(buildDir, 'unsigned.apk');
const alignedApk = path.join(buildDir, 'aligned.apk');
const finalApk = path.join(projectRoot, 'TouchRacerEnhanced.apk');

const env = {
  ...process.env,
  JAVA_HOME: jdkRoot,
  PATH: `${path.join(jdkRoot, 'bin')};${process.env.PATH}`
};

console.log('=== Building Touch Racer Enhanced APK ===');

[buildDir, compiledResDir, genDir, classesDir].forEach(d => {
  if (fs.existsSync(d)) fs.rmSync(d, { recursive: true, force: true });
  fs.mkdirSync(d, { recursive: true });
});

console.log('[1/6] Compiling resources with aapt2...');
const resDir = path.join(projectRoot, 'app', 'src', 'main', 'res');
execSync(`"${aapt2}" compile --dir "${resDir}" -o "${compiledResDir}"`, { env, stdio: 'inherit' });

console.log('[2/6] Linking resources with aapt2 link...');
const manifest = path.join(projectRoot, 'app', 'src', 'main', 'AndroidManifest.xml');
const flatFiles = fs.readdirSync(compiledResDir).map(f => `"${path.join(compiledResDir, f)}"`).join(' ');
execSync(`"${aapt2}" link -I "${androidJar}" --min-sdk-version 21 --target-sdk-version 34 --manifest "${manifest}" ${flatFiles} -o "${unsignedApk}" --java "${genDir}" --auto-add-overlay`, { env, stdio: 'inherit' });

console.log('[3/6] Compiling Java code with javac...');
const javaSrcDir = path.join(projectRoot, 'app', 'src', 'main', 'java', 'com', 'masterjangkir', 'touchracer');
const javaFiles = fs.readdirSync(javaSrcDir).map(f => path.join(javaSrcDir, f));
const rJava = path.join(genDir, 'com', 'masterjangkir', 'touchracer', 'R.java');
const allSources = [...javaFiles, rJava].map(f => `"${f.replace(/\\/g, '/')}"`).join(' ');
execSync(`"${javac}" -cp "${androidJar.replace(/\\/g, '/')}" -d "${classesDir.replace(/\\/g, '/')}" -source 1.8 -target 1.8 ${allSources}`, { env, stdio: 'inherit' });

console.log('[4/6] Dexing with d8...');
function findClasses(dir) {
  let results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results = results.concat(findClasses(full));
    } else if (entry.name.endsWith('.class')) {
      results.push(`"${full.replace(/\\/g, '/')}"`);
    }
  }
  return results;
}
const classList = findClasses(classesDir).join(' ');
execSync(`"${d8}" --lib "${androidJar.replace(/\\/g, '/')}" --output "${buildDir.replace(/\\/g, '/')}" ${classList}`, { env, stdio: 'inherit' });

console.log('[5/6] Packaging classes.dex into APK...');
execSync(`"${jar}" uf "${unsignedApk}" -C "${buildDir}" classes.dex`, { env, stdio: 'inherit' });

console.log('[6/6] Aligning and signing APK...');
if (fs.existsSync(alignedApk)) fs.unlinkSync(alignedApk);
execSync(`"${zipalign}" -p -f 4 "${unsignedApk}" "${alignedApk}"`, { env, stdio: 'inherit' });

const keystore = path.join(buildDir, 'debug.keystore');
if (!fs.existsSync(keystore)) {
  execSync(`"${keytool}" -genkeypair -v -keystore "${keystore}" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`, { env, stdio: 'inherit' });
}

if (fs.existsSync(finalApk)) fs.unlinkSync(finalApk);
execSync(`"${apksigner}" sign --ks "${keystore}" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "${finalApk}" "${alignedApk}"`, { env, stdio: 'inherit' });

const stat = fs.statSync(finalApk);
console.log('\n===================================================');
console.log('SUCCESS! TouchRacerEnhanced.apk generated successfully!');
console.log('Location:', finalApk);
console.log('File Size:', (stat.size / 1024).toFixed(1), 'KB');
console.log('===================================================');
