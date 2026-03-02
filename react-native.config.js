module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.easy.bg.BackgroundUploadPackage;',
        packageInstance: 'new BackgroundUploadPackage()',
      },
      ios: {
        podspecPath: 'ios/BackgroundUpload.podspec',
      },
    },
  },
};
