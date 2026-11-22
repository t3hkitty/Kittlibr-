"""
plugin_registry.py — Calibre-Style Sub-Plugin Harness for Kittlibr librmanage
Models extension lifecycle hooks after calibre.customize:
  - MetadataReaderPlugin
  - MetadataWriterPlugin
  - CatalogTransformPlugin
  - AcquisitionPlugin (librget)
  - ScraperPlugin (librscrape)
Enables librget, librscrape, librfind, and libr_ai to plug directly into the ingest & catalog pipeline.
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional
from pathlib import Path


class MetadataReaderPlugin(ABC):
    @abstractmethod
    def read_metadata(self, file_path: str) -> Dict[str, Any]:
        pass


class MetadataWriterPlugin(ABC):
    @abstractmethod
    def write_metadata(self, file_path: str, metadata: Dict[str, Any]) -> bool:
        pass


class CatalogTransformPlugin(ABC):
    @abstractmethod
    def transform_catalog(self, catalog: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        pass


class AcquisitionPlugin(ABC):
    """Slot for librget (OpenlibExtended / shadow libraries search and download)"""
    @abstractmethod
    def search(self, query: str, limit: int = 10) -> List[Dict[str, Any]]:
        pass

    @abstractmethod
    def enqueue_download(self, item_id: str, target_dir: str) -> Dict[str, Any]:
        pass


class ScraperPlugin(ABC):
    """Slot for librscrape (Web serials / WebToEpub / lncrawl EPUB 3 packager)"""
    @abstractmethod
    def scrape_and_package(self, novel_url: str, target_dir: str, chapter_limit: Optional[int] = None) -> Dict[str, Any]:
        pass


class SubPluginRegistry:
    def __init__(self):
        self.readers: List[MetadataReaderPlugin] = []
        self.writers: List[MetadataWriterPlugin] = []
        self.transformers: List[CatalogTransformPlugin] = []
        self.acquisition_plugins: Dict[str, AcquisitionPlugin] = {}
        self.scraper_plugins: Dict[str, ScraperPlugin] = {}

    def register_reader(self, plugin: MetadataReaderPlugin):
        self.readers.append(plugin)

    def register_writer(self, plugin: MetadataWriterPlugin):
        self.writers.append(plugin)

    def register_transformer(self, plugin: CatalogTransformPlugin):
        self.transformers.append(plugin)

    def register_acquisition_plugin(self, name: str, plugin: AcquisitionPlugin):
        self.acquisition_plugins[name] = plugin

    def register_scraper_plugin(self, name: str, plugin: ScraperPlugin):
        self.scraper_plugins[name] = plugin


# Global registry singleton
REGISTRY = SubPluginRegistry()
