import json
from pathlib import Path
import glob
from rdflib import URIRef, Literal, BNode, Graph, ConjunctiveGraph
from rdflib.namespace import RDF, RDFS, SKOS, OWL, Namespace, NamespaceManager, XSD
from tqdm import tqdm
import sys
import re

#GITPATH = "/home/eroux/BUDA/softs/xmltoldmigration/tbrc-ttl/iinstances"
GITPATH = "../xmltoldmigration/tbrc-ttl/"
if len(sys.argv) > 1:
    GITPATH = sys.argv[1]


PREFIXMAP = {}

BDR = Namespace("http://purl.bdrc.io/resource/")
BDO = Namespace("http://purl.bdrc.io/ontology/core/")
TMP = Namespace("http://purl.bdrc.io/ontology/tmp/")
BDA = Namespace("http://purl.bdrc.io/admindata/")
ADM = Namespace("http://purl.bdrc.io/ontology/admin/")

NSM = NamespaceManager(Graph())
NSM.bind("bdr", BDR)
NSM.bind("bdo", BDO)
NSM.bind("tmp", TMP)
NSM.bind("bda", BDA)
NSM.bind("adm", ADM)
NSM.bind("skos", SKOS)
NSM.bind("rdfs", RDFS)

def analyzeLName(lname):
    typeprefix = ""
    userprefix = ""
    if lname[:3] in ["WAS", "ITW", "PRA"]:
        typeprefix = lname[:3]
        lname = lname[3:]
    elif lname[:2] in ["WA", "MW", "PR", "IE", "UT", "IT"]:
        typeprefix = lname[:2]
        lname = lname[2:]
    else:
        typeprefix = lname[:1]
        if typeprefix not in ["W", "P", "G", "R", "L", "C", "T", "I"]:
            print("no1")
            return None
        lname = lname[1:]
    mtch = re.search(r"^\d\d?[A-Z][A-Z][A-Z]?[A-Z]?[A-Z]?", lname)
    if mtch is not None:
        l = len(mtch.group(0))
        userprefix = lname[:l]
        lname = lname[l:]
    lodashidx = lname.find("_")
    if lodashidx > -1:
        lname = lname[:lodashidx]
    idx = 0
    try:
        idx = int(lname)
    except ValueError:
        print("no2")
        return None
    prefix = typeprefix + userprefix
    return [prefix, idx]

def handleFile(fpath):
    lname = Path(fpath).stem
    if "WEAP" in lname or lname.startswith("WA0XL"):
        return
    parts = analyzeLName(lname)
    if parts == None:
        print("non-conform ID: %s" % lname)
        return
    prefix = parts[0]
    idx = parts[1]
    if prefix not in PREFIXMAP:
        PREFIXMAP[prefix] = idx
        return
    curidx = PREFIXMAP[prefix]
    if curidx < idx:
        PREFIXMAP[prefix] = idx
    if re.match(r"W\d", lname):
        analyzeIinstance(fpath)


def analyzeIinstance(iiFilePath):
    model = ConjunctiveGraph()
    model.parse(str(iiFilePath), format="trig")
    for _, _, v in model.triples((None, BDO.instanceHasVolume, None)):
        _, _, vLname = NSM.compute_qname_strict(v)
        parts = analyzeLName(vLname)
        if parts == None:
            print("non-conform ID: %s" % vLname)
            return
        prefix = parts[0]
        idx = parts[1]
        if prefix not in PREFIXMAP:
            PREFIXMAP[prefix] = idx
            return
        curidx = PREFIXMAP[prefix]
        if curidx < idx:
            PREFIXMAP[prefix] = idx
    

def main():
    l = sorted(glob.glob(GITPATH+'/**/*.trig', recursive=True))
    i = 0
    for fpath in tqdm(l):
        handleFile(fpath)
        i += 1
        #if i > 400:
        #    break
    print(PREFIXMAP)

main()