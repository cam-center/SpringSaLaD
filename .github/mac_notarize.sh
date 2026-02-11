set -x
set +e

if [[ "$#" != 4 ]]; then
  echo "usage: $0 MAC_TEAM_ID MAC_ID MAC_PW MAC_INSTALLER"
  echo "  MAC_TEAM_ID: Apple Developer Team ID"
  echo "  MAC_ID: Apple ID email address"
  echo "  MAC_PW: App-specific password for Apple ID"
  echo "  MAC_INSTALLER: path to Mac installer to be notarized"
  exit 1
fi

MAC_TEAM_ID=$1
MAC_ID=$2
MAC_PW=$3
MAC_INSTALLER=$4

xcrun notarytool submit --output-format normal --no-progress --no-wait --team-id "${MAC_TEAM_ID}" --apple-id "${MAC_ID}" --password "${MAC_PW}" "$MAC_INSTALLER" > submit_output
echo "output returned by notarytool submit:"
cat submit_output
cat submit_output | grep "id:" | cut -d ':' -f2 > UUID
for minutes in {1..5}
do
  sleep 60
  xcrun notarytool info --output-format normal --no-progress --team-id "${MAC_TEAM_ID}" --apple-id "${MAC_ID}" --password "${MAC_PW}" `cat UUID` > info_output
  echo "output returned by notarytool info:"
  cat info_output
  grep -q Accepted info_output
  if [[ $? == 0 ]]; then
    echo "notarized succesfully"
    break
  else
    echo "wait another minute and check again"
  fi
done
grep -q Accepted info_output
if [[ $? == 0 ]]; then
  xcrun stapler staple "$MAC_INSTALLER"
else
  echo "notarization did not succeed in 5 minutes, giving up - asking for notary log"
  xcrun notarytool log --verbose --output-format normal --no-progress --team-id "${MAC_TEAM_ID}" --apple-id "${MAC_ID}" --password "${MAC_PW}" `cat UUID`
  exit 1
fi

