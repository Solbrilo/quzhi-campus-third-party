import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { HvigorNode, HvigorPlugin } from '@ohos/hvigor';
import { appTasks, OhosAppContext, OhosPluginId } from '@ohos/hvigor-ohos-plugin';

const localSigningConfigPath = resolve(__dirname, 'signing.local.json');
const localSigningConfig = existsSync(localSigningConfigPath)
    ? JSON.parse(readFileSync(localSigningConfigPath, 'utf8'))
    : undefined;

const requireLocalReleaseSigning: HvigorPlugin = {
    pluginId: 'hotwater.require-local-release-signing',
    apply(node: HvigorNode) {
        node.afterNodeEvaluate(() => {
            const appContext = node.getContext(OhosPluginId.OHOS_APP_PLUGIN) as OhosAppContext | undefined;
            if (appContext?.getBuildMode() === 'release' && !localSigningConfig) {
                throw new Error(
                    'Release builds require harmony_hotwater/signing.local.json. Copy signing.local.example.json and provide local signing materials.'
                );
            }
        });
    }
};

export default {
    system: appTasks,
    plugins: [requireLocalReleaseSigning],
    config: localSigningConfig ? {
        ohos: {
            overrides: {
                signingConfig: localSigningConfig
            }
        }
    } : undefined
};
