package com.emc.storageos.glance.model;

import java.util.ArrayList;
import java.util.List;

public class ImageList {

private List<Image> images = new ArrayList<Image>();

/**
* 
* @return
* The images
*/
public List<Image> getImages() {
return images;
}

/**
* 
* @param images
* The images
*/
public void setImages(List<Image> images) {
this.images = images;
}

}