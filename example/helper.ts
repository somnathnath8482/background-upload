import * as ImagePicker from "expo-image-picker";

export const pickMedia = async (
  type = "image",
  limit = 1,
  onSelect = (ast: ImagePicker.ImagePickerAsset[]) => {},
) => {
  let result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: type === "image" ? ["images"] : ["videos"],
    allowsMultipleSelection: limit == 1 ? false : true, // Allow multiple images
    selectionLimit: limit,
    quality: 1,
  });

  if (!result.canceled) {
    const selectedMedia = result.assets;
    onSelect(selectedMedia);
  }
};
