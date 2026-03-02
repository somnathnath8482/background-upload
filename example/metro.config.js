// Learn more https://docs.expo.io/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const config = getDefaultConfig(__dirname);
const appNodeModules = path.resolve(__dirname, 'node_modules');
const rootNodeModules = path.resolve(__dirname, '..', 'node_modules');

function toBlockRegex(targetPath) {
  const escaped = targetPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped}[/\\\\].*`);
}

config.resolver.nodeModulesPaths = [
  appNodeModules,
];

config.resolver.extraNodeModules = {
  'background-upload': '..',
  react: path.resolve(appNodeModules, 'react'),
  'react-native': path.resolve(appNodeModules, 'react-native'),
};

config.resolver.blockList = [
  ...Array.from(config.resolver.blockList ?? []),
  toBlockRegex(path.resolve(rootNodeModules, 'react')),
  toBlockRegex(path.resolve(rootNodeModules, 'react-native')),
];

config.resolver.disableHierarchicalLookup = true;

config.watchFolders = [path.resolve(__dirname, '..')];

config.transformer.getTransformOptions = async () => ({
  transform: {
    experimentalImportSupport: false,
    inlineRequires: true,
  },
});

module.exports = config;
