#!/usr/bin/env python3
"""Compile and execute policy tests using the Kotlin compiler resolved by the Android build."""
import argparse
import logging
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    args = parser.parse_args()
    base = args.gradle_cache
    artifacts = [
        'org.jetbrains.kotlin/kotlin-compiler-embeddable',
        'org.jetbrains.kotlin/kotlin-stdlib',
        'org.jetbrains.kotlin/kotlin-script-runtime',
        'org.jetbrains.kotlin/kotlin-reflect',
        'org.jetbrains.intellij.deps/trove4j',
        'org.jetbrains/annotations',
        'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm',
    ]
    jars = sorted(str(jar) for artifact in artifacts for jar in (base / artifact).glob('*/*/*.jar'))
    if not jars or not any('kotlin-compiler-embeddable' in jar for jar in jars):
        raise RuntimeError('Run the Android Gradle build first to resolve its Kotlin compiler')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    classpath = os.pathsep.join(jars)
    policy = args.source / 'client/android/src/org/amnezia/vpn/HomeWifiPolicy.kt'
    harness = Path(__file__).with_name('HomeWifiPolicyTest.kt')
    with tempfile.TemporaryDirectory(prefix='amnezia-home-policy-') as output:
        subprocess.run([java, '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                        '-no-stdlib', '-no-reflect', '-classpath', classpath, '-d', output,
                        str(policy), str(harness)], check=True)
        subprocess.run([java, '-cp', os.pathsep.join([output, classpath]),
                        'org.amnezia.vpn.HomeWifiPolicyTestKt'], check=True)
    logging.info('Network policy tests passed')


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
    main()
