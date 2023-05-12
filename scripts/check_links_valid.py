#!/bin/python
import re
import glob
import os
import sys
import urllib.request
from urllib.error import HTTPError
from urllib.error import URLError

root_dir="docs"
readme="README.md"

def request(url):
    try:
        print(f"requesting... ---{url}--- ")
        if response := urllib.request.urlopen(url):
            return response.getcode() == 200
        print("No response\n")
        return False
    except HTTPError as e:
        print("HTTPError \n")
        return False
    except URLError as e:
        print("URLError\n")
        return False
    print("\n")
    return False

def findStripeLinksInFile(filename):
    allUrls = []
    if isFile := os.path.isfile(filename):
        regexStripe = r'(https://stripe.com[^\\)|"|\\ |<]*)'
        regexGithub = r'(https://github.com[^\\)|"|\\ |<]*)'
        with open(filename) as file:
            for line in file:
                allUrls.extend(re.findall(regexStripe, line))
                allUrls.extend(re.findall(regexGithub, line))
    return allUrls


def findStripeLinks(root_dir):
    urlSet=set()
    for filename in glob.iglob(f'{root_dir}**/**', recursive=True):
        if urls := findStripeLinksInFile(filename):
            for url in urls:
                urlSet.add(url)

    return urlSet

##------
# Find the stripe links in the documentation directory
urlSet = findStripeLinks(root_dir)
# Find the stripe links in the Readme
for urlLinkFromReadme in findStripeLinksInFile("README.md"):
  urlSet.add(urlLinkFromReadme)

urlDNE = [url for url in urlSet if (not request(url))]
print(urlDNE)
sys.exit(1)
