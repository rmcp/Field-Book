package com.fieldbook.tracker.adapters

import android.content.res.Configuration
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import java.io.FileNotFoundException


/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * The ImageAdapter is used in the abstract camera trait view. It is used to display a horizontal
 * scrolling list of images, each with a close button to delete the image. The last viewable list item
 * is a camerax preview view or an image view, depending on the actual implementation. By default, for system/camerax
 * the preview view is used, which has a shutter button, a settings button, and an 'embiggen' button that
 * starts a fullscreen capture.
 */
class ImageAdapter(private val listener: ImageItemHandler, private val thumbnailSize: Point) :
        ListAdapter<ImageAdapter.Model, ImageAdapter.ViewHolder>(DiffCallback()) {

    enum class Type {
        IMAGE,
        PREVIEW
    }

    data class Model(
        val type: Type = Type.IMAGE,
        val orientation: Int = Configuration.ORIENTATION_PORTRAIT,
        var uri: String? = null,
        var brapiSynced: Boolean? = null
    )

    interface ImageItemHandler {

        fun onItemClicked(model: Model)

        fun onItemDeleted(model: Model)

    }

    abstract inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(model: Model)
    }

    inner class ImageViewHolder(private val view: View) : ViewHolder(view) {

        val cardView: CardView = view.findViewById(R.id.list_item_image_cv)
        val imageView: ImageView = view.findViewById(R.id.list_item_image_iv)
        val closeButton: ImageButton = view.findViewById(R.id.list_item_image_close_btn)

        init {
            // Define click listener for the ViewHolder's View.
            view.setOnClickListener {
                listener.onItemClicked(view.tag as Model)
            }

            closeButton.setOnClickListener {
                listener.onItemDeleted(view.tag as Model)
            }
        }

        override fun bind(model: Model) {

            itemView.tag = model

            try {

                //get preview image dimensions from resources
                val previewWidth = view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_width)
                val previewHeight = view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_height)

                (cardView.layoutParams as ConstraintLayout.LayoutParams).apply {

                    if (model.orientation == Configuration.ORIENTATION_PORTRAIT) {

                        width = previewWidth
                        height = previewHeight

                    } else {

                        width = previewHeight
                        height = previewWidth

                    }
                }

                DocumentsContract.getDocumentThumbnail(
                    view.context.contentResolver,
                    Uri.parse(model.uri), thumbnailSize, null
                )?.let { bmp ->

                    imageView.setImageBitmap(bmp)

                }

            } catch (f: FileNotFoundException) {

                f.printStackTrace()

            }
        }
    }

    inner class PreviewViewHolder(view: View) : ViewHolder(view) {

        val previewView: PreviewView = view.findViewById(R.id.trait_camera_pv)
        val embiggenButton: ImageButton = view.findViewById(R.id.trait_camera_expand_btn)

        override fun bind(model: Model) {

        }
    }

    override fun getItemViewType(position: Int): Int {
        return currentList[position].type.ordinal
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {

        // Create a new view, which defines the UI of the list item
        return when (viewType) {

            //inflate and create the preview view list item
            Type.PREVIEW.ordinal -> {

                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_camera_preview, viewGroup, false)

                PreviewViewHolder(view)
            }

            else -> {

                //inflate and create the image view list item
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_image, viewGroup, false)

                ImageViewHolder(view)
            }
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.bind(this)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }
    }
}