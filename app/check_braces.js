const { execSync } = require('child_process');
try {
    const diff = execSync('git diff app/src/main/java/com/example/ui/AlterAppScreen.kt', { encoding: 'utf8' });
    console.log(diff);
} catch (e) {
    console.error('Error running git diff:', e.message);
}
